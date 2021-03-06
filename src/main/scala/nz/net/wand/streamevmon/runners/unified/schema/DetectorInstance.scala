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

package nz.net.wand.streamevmon.runners.unified.schema

import nz.net.wand.streamevmon.events.Event
import nz.net.wand.streamevmon.flink.HasFlinkConfig
import nz.net.wand.streamevmon.measurements.amp._
import nz.net.wand.streamevmon.measurements.bigdata.Flow
import nz.net.wand.streamevmon.measurements.esmond._
import nz.net.wand.streamevmon.measurements.latencyts.{LatencyTSAmpICMP, LatencyTSSmokeping}
import nz.net.wand.streamevmon.Perhaps._
import nz.net.wand.streamevmon.measurements.amp2.{Traceroute => A2Traceroute, _}
import nz.net.wand.streamevmon.measurements.nab.NabMeasurement
import nz.net.wand.streamevmon.measurements.traits.Measurement

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.Window

/** Represents a configuration for a detector. Detector type is informed by
  * the DetectorSchema which holds this class. Measurement type is informed by
  * the sources involved.
  *
  * @param sources References the source's user-defined name, the datatype to
  *                get from it, and whether or not to filter lossy measurements
  *                out.
  * @param sinks   References each sink that should be used with this detector.
  * @param config  Any configuration overrides to be passed to the detector.
  */
case class DetectorInstance(
  @JsonProperty("source")
  sources: Iterable[SourceReference],
  @JsonProperty("sink")
  sinks: Iterable[SinkReference],
  config : Map[String, String] = Map()
) {

  /** Builds a detector instance with the appropriate measurement type. */
  def buildKeyed(
    detType: DetectorType.ValueBuilder
  ): KeyedProcessFunction[String, Measurement, Event] with HasFlinkConfig = {
    // Currently, all detectors have a single input type.
    val source = sources.headOption.getOrElse(
      throw new IllegalArgumentException("Detector instance must have at least one source!")
    )
    // The detector type knows how to build itself, but it needs to know what
    // type its measurements will be.
    val det = source.datatype match {
      // Amp
      case SourceDatatype.DNS => detType.buildKeyed[DNS]
      case SourceDatatype.HTTP => detType.buildKeyed[HTTP]
      case SourceDatatype.ICMP => detType.buildKeyed[ICMP]
      case SourceDatatype.TCPPing => detType.buildKeyed[TCPPing]
      case SourceDatatype.TraceroutePathlen => detType.buildKeyed[TraceroutePathlen]
      // Amp2
      case SourceDatatype.External => detType.buildKeyed[External]
      case SourceDatatype.Fastping => detType.buildKeyed[Fastping]
      case SourceDatatype.Amp2Http => detType.buildKeyed[Http]
      case SourceDatatype.Latency => detType.buildKeyed[Latency]
      case SourceDatatype.LatencyDns => detType.buildKeyed[LatencyDns]
      case SourceDatatype.LatencyIcmp => detType.buildKeyed[LatencyIcmp]
      case SourceDatatype.LatencyTcpping => detType.buildKeyed[LatencyTcpping]
      case SourceDatatype.Pathlen => detType.buildKeyed[Pathlen]
      case SourceDatatype.Sip => detType.buildKeyed[Sip]
      case SourceDatatype.Throughput => detType.buildKeyed[Throughput]
      case SourceDatatype.Traceroute => detType.buildKeyed[A2Traceroute]
      case SourceDatatype.Udpstream => detType.buildKeyed[Udpstream]
      case SourceDatatype.Video => detType.buildKeyed[Video]
      // Bigdata
      case SourceDatatype.Flow => detType.buildKeyed[Flow]
      // Esmond
      case SourceDatatype.Failure => detType.buildKeyed[Failure]
      case SourceDatatype.Histogram => detType.buildKeyed[Histogram]
      case SourceDatatype.Href => detType.buildKeyed[Href]
      case SourceDatatype.PacketTrace => detType.buildKeyed[PacketTrace]
      case SourceDatatype.Simple => detType.buildKeyed[Simple]
      case SourceDatatype.Subinterval => detType.buildKeyed[Subinterval]
      // Latency TS
      case SourceDatatype.LatencyTSAmp => detType.buildKeyed[LatencyTSAmpICMP]
      case SourceDatatype.LatencyTSSmokeping => detType.buildKeyed[LatencyTSSmokeping]
      // NAB
      case SourceDatatype.NAB => detType.buildKeyed[NabMeasurement]
      case d => throw new IllegalArgumentException(s"Unknown datatype $d!")
    }

    det.overrideConfig(config, s"detector.${det.configKeyGroup}")
  }

  def buildWindowed(
    detType: DetectorType.ValueBuilder
  ): (ProcessWindowFunction[Measurement, Event, String, Window] with HasFlinkConfig, StreamWindowType.Value) = {
    val source = sources.headOption.getOrElse(
      throw new IllegalArgumentException("Detector instance must have at least one source!")
    )

    val det = source.datatype match {
      // Amp
      case SourceDatatype.DNS => detType.buildWindowed[DNS]
      case SourceDatatype.HTTP => detType.buildWindowed[HTTP]
      case SourceDatatype.ICMP => detType.buildWindowed[ICMP]
      case SourceDatatype.TCPPing => detType.buildWindowed[TCPPing]
      case SourceDatatype.TraceroutePathlen => detType.buildWindowed[TraceroutePathlen]
      // Amp2
      case SourceDatatype.External => detType.buildWindowed[External]
      case SourceDatatype.Fastping => detType.buildWindowed[Fastping]
      case SourceDatatype.Amp2Http => detType.buildWindowed[Http]
      case SourceDatatype.Latency => detType.buildWindowed[Latency]
      case SourceDatatype.LatencyDns => detType.buildWindowed[LatencyDns]
      case SourceDatatype.LatencyIcmp => detType.buildWindowed[LatencyIcmp]
      case SourceDatatype.LatencyTcpping => detType.buildWindowed[LatencyTcpping]
      case SourceDatatype.Pathlen => detType.buildWindowed[Pathlen]
      case SourceDatatype.Sip => detType.buildWindowed[Sip]
      case SourceDatatype.Throughput => detType.buildWindowed[Throughput]
      case SourceDatatype.Traceroute => detType.buildWindowed[A2Traceroute]
      case SourceDatatype.Udpstream => detType.buildWindowed[Udpstream]
      case SourceDatatype.Video => detType.buildWindowed[Video]
      // Bigdata
      case SourceDatatype.Flow => detType.buildWindowed[Flow]
      // Esmond
      case SourceDatatype.Failure => detType.buildWindowed[Failure]
      case SourceDatatype.Histogram => detType.buildWindowed[Histogram]
      case SourceDatatype.Href => detType.buildWindowed[Href]
      case SourceDatatype.PacketTrace => detType.buildWindowed[PacketTrace]
      case SourceDatatype.Simple => detType.buildWindowed[Simple]
      case SourceDatatype.Subinterval => detType.buildWindowed[Subinterval]
      // Latency TS
      case SourceDatatype.LatencyTSAmp => detType.buildWindowed[LatencyTSAmpICMP]
      case SourceDatatype.LatencyTSSmokeping => detType.buildWindowed[LatencyTSSmokeping]
      // NAB
      case SourceDatatype.NAB => detType.buildWindowed[NabMeasurement]
      case d => throw new IllegalArgumentException(s"Unknown datatype $d!")
    }

    (
      det._1.overrideConfig(config, s"detector.${det._1.configKeyGroup}"),
      det._2
    )
  }
}
