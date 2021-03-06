/* This file is part of streamevmon.
 *
 * Copyright (C) 2021  The University of Waikato, Hamilton, New Zealand
 *
 * Author: Daniel Oosterwijk
 *
 * All rights reserved.
 *
 * This code has been developed by the University of Waikato WAND
 * research group. For further information please see https://wand.nz,
 * or our Github organisation at https://github.com/wanduow
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nz.net.wand.streamevmon.runners.unified

import nz.net.wand.streamevmon.{Configuration, Lazy, Logging}
import nz.net.wand.streamevmon.events.{Event, FrequentEventFilter}
import nz.net.wand.streamevmon.events.grouping.EventGrouperFlinkHelper
import nz.net.wand.streamevmon.flink.HasFlinkConfig
import nz.net.wand.streamevmon.flink.sinks.{InfluxEventGroupSink, InfluxEventSink}
import nz.net.wand.streamevmon.measurements.traits.Measurement
import nz.net.wand.streamevmon.measurements.MeasurementTimestampAssigner
import nz.net.wand.streamevmon.parameters.HasParameterSpecs
import nz.net.wand.streamevmon.runners.unified.schema.{StreamToTypedStreams, StreamWindowType}

import java.time.Duration
import java.util.concurrent.TimeUnit

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.api.CheckpointingMode
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time

import scala.collection.mutable
import scala.compat.Platform.EOL
import scala.util.Try

/** Reads a flow configuration from a YAML file, and constructs and executes
  * a Flink pipeline for it.
  */
object YamlDagRunner extends Logging {

  def main(args: Array[String]): Unit = {
    // == Setup flink config ==
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val config = Configuration.get(args)
    env.getConfig.setGlobalJobParameters(config)

    env.disableOperatorChaining

    env.enableCheckpointing(
      Duration.ofSeconds(config.getInt("flink.checkpointInterval")).toMillis,
      CheckpointingMode.EXACTLY_ONCE
    )

    env.setRestartStrategy(RestartStrategies.noRestart())

    // == Parse flow config key ==
    val flows = Configuration.getFlowsDag()

    // We keep track of which detectors subscribe to each sink as they're created,
    // so that we can tie them together once we've made all our detectors.
    // detectorsBySink and sinks share the same keys, since they have related values.
    val detectorsBySink: mutable.Map[String, mutable.Buffer[DataStream[Event]]] = mutable.Map()

    // We don't need to build sinks lazily since they don't get added to the
    // execution plan unless they get tied to a detector.
    val sinks: Map[String, SinkFunction[Event] with HasFlinkConfig] =
    flows.sinks.map {
      case (name, sink) =>
        detectorsBySink(name) = mutable.Buffer()
        (name, sink.build)
    }

    // We build all our sources lazily. This means that if their output isn't
    // used, they won't be constructed and won't appear in the execution plan.
    // Since sources get tied to the environment rather than another stream,
    // they'll appear regardless of if they're used if they're not built lazily.
    val sources: Map[String, StreamToTypedStreams] =
    flows.sources.map {
      case (name, sourceInstance) =>
        val lazyBuilt = new Lazy({
          Try(sourceInstance.buildSourceFunction).toOption.fold {
            val (format, filter) = sourceInstance.buildFileInputFormat
            val formatConf = format.configWithOverride(config)

            val configPrefixNoSubtype = s"source.${sourceInstance.sourceType}"
            val configPrefix = sourceInstance.sourceSubtype
              .map(s => s"$configPrefixNoSubtype.$s")
              .getOrElse(configPrefixNoSubtype)

            format.setFilesFilter(filter(formatConf))

            val timestampAssigner = new MeasurementTimestampAssigner

            env
              .readFile(
                format,
                formatConf.get(s"$configPrefix.location")
              )
              .setParallelism(1)
              .name(s"$name (${format.flinkName})")
              .uid(s"${format.flinkUid}-$name")
              .assignTimestampsAndWatermarks(
                WatermarkStrategy
                  .forBoundedOutOfOrderness[Measurement](Duration.ofSeconds(config.getInt("flink.maxLateness")))
                  .withTimestampAssigner(timestampAssigner)
              )
          } { sourceFunction =>
            env
              .addSource(sourceFunction)
              .name(s"$name (${sourceFunction.flinkName})")
              .uid(s"${sourceFunction.flinkUid}-$name")
          }
        })

        (
          name,
          StreamToTypedStreams(lazyBuilt, sourceInstance)
        )
    }

    // Time to build detectors and tie them to I/O.
    flows.detectors.foreach {
      case (name, detSchema) =>
        // Each schema can have several instances.
        detSchema.instances.zipWithIndex.foreach { case (detInstance, index) =>
          // Each instance can have several sources...
          val sourcesList = detInstance.sources.map(s => (s, sources(s.name)))
          // ... but there's only one per detector for now.
          val eventStream = sourcesList.headOption
            .map {
              case (srcReference, stream) =>
                // The best way to get the new detector with its correctly
                // overridden config is to just build it and let HasFlinkConfig
                // do its magic. Building the detector also enforces that the
                // measurement type has the right attributes for the detector,
                // like HasDefault.
                val keyedDetector = detInstance.buildKeyed(detSchema.detType)
                val detConf = keyedDetector.configWithOverride(config)

                // Now that we have the config for this detector, we should
                // check that the parameters it was given are valid.
                HasParameterSpecs.parameterToolIsValid(detConf, throwException = true)

                // If we end out only needing the windowed version of the
                // detector, the keyed one will just get garbage collected. That's fine.
                if (detConf.getBoolean(s"detector.${keyedDetector.configKeyGroup}.useFlinkTimeWindow")) {
                  // Since we know we want the windowed version now, let's build it.
                  val (windowedDetector, windowType) = detInstance.buildWindowed(detSchema.detType)

                  // We'll also grab the config settings while we're at it.
                  // Detectors might provide their own overrides, but if they
                  // don't, we'll use the default method.
                  val timeWindowDuration = windowType match {
                    case t: StreamWindowType.TimeWithOverrides if t.size.isDefined => Time.of(t.size.get(detConf), TimeUnit.SECONDS)
                    case _ => Time.of(detConf.getLong(s"detector.${windowedDetector.configKeyGroup}.windowDuration"), TimeUnit.SECONDS)
                  }

                  val countWindowSize = windowType match {
                    case t: StreamWindowType.CountWithOverrides if t.size.isDefined => t.size.get(detConf)
                    case _ => detConf.getLong(s"detector.${windowedDetector.configKeyGroup}.windowSize")
                  }

                  val countWindowSlide = windowType match {
                    case t: StreamWindowType.CountWithOverrides if t.slide.isDefined => t.slide.get(detConf)
                    case _ => detConf.getLong(s"detector.${windowedDetector.configKeyGroup}.windowSlide")
                  }

                  // Finally, let's turn our source into a windowed stream...
                  val windowedStream = stream
                    .typedAs(srcReference.datatype)
                    .getWindowedStream(
                      srcReference.name,
                      srcReference.filterLossy,
                      windowType,
                      timeWindowDuration,
                      countWindowSize,
                      countWindowSlide
                    )

                  // ... and hook it into the detector.
                  (
                    windowedStream
                      .process(windowedDetector)
                      .name(s"$name (${windowedDetector.flinkName})")
                      .uid(s"${windowedDetector.flinkUid}-$name-$index"),
                    name,
                    s"${windowedDetector.flinkUid}-$name-$index"
                  )
                }
                else {
                  // We only grab the appropriate stream. The unused ones don't
                  // get built, since they're lazy.
                  val selectedStream = if (srcReference.filterLossy) {
                    stream.typedAs(srcReference.datatype).notLossyKeyedStream
                  }
                  else {
                    stream.typedAs(srcReference.datatype).keyedStream
                  }

                  // Hook in the source.
                  (
                    selectedStream
                      .process(keyedDetector)
                      .name(s"$name (${keyedDetector.flinkName})")
                      .uid(s"${keyedDetector.flinkUid}-$name-$index"),
                    name,
                    s"${keyedDetector.flinkUid}-$name-$index"
                  )
                }
            }
            .getOrElse(
              throw new IllegalArgumentException("Detector instance must have at least one source!")
            )

          // Slap a filtering step in to quiet down all those noisy detectors
          val filteredEventStream = {
            val func = new FrequentEventFilter
            eventStream._1
              .keyBy(_.stream)
              .process(func)
              .name(s"${func.flinkName} (${eventStream._2})")
              .uid(s"${func.flinkUid}-${eventStream._3}")
          }

          // Register the instance for the sinks it wants.
          detInstance.sinks.foreach { sink =>
            detectorsBySink(sink.name).append(filteredEventStream)
          }
        }
    }

    // Now that we've made all our detctors, we can go ahead and tie them to
    // their sinks.
    // Everyone gets a free EventGrouper pipeline in here, because extending
    // the configuration to do it properly is going to take a while and doesn't
    // seem worth it right now.
    detectorsBySink.foreach {
      case (sinkName, dets) =>
        // We need to tie all the detector outputs together into one DataStream
        // so that there's only one sink instance.
        val union = dets.size match {
          case 0 => None
          case 1 => Some(dets.head)
          case _ => Some(dets.head.union(dets.drop(1): _*))
        }

        union.map { dets =>
          dets
            .addSink(sinks(sinkName))
            .name(s"$sinkName (${sinks(sinkName).flinkName})")
            .uid(s"${sinks(sinkName).flinkUid}-$sinkName")

          if (sinks(sinkName).isInstanceOf[InfluxEventSink]) {
            val grouping = EventGrouperFlinkHelper.addGrouping(config, dets)
            val groupSink = new InfluxEventGroupSink
            val groupedEventSink = new InfluxEventSink

            grouping.addSink(groupSink)
              .name(groupSink.flinkName)
              .uid(s"${groupSink.flinkUid}-eventgroups")

            grouping
              .flatMap(_.events)
              .name(s"${groupSink.flinkName} - Extract Grouped Events")
              .uid(s"${groupSink.flinkUid}-grouped-events-extractor")
              .addSink(groupedEventSink)
              .name(s"${groupedEventSink.flinkName} - Sink Grouped Events")
              .uid(s"${groupedEventSink.flinkUid}-grouped-events-sink")
          }
        }
    }

    logger.info(s"Execution plan: ${env.getExecutionPlan.replace(EOL, "")}")
    env.execute()
  }
}
