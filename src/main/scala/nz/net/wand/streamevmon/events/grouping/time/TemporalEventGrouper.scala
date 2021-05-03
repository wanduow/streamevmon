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

package nz.net.wand.streamevmon.events.grouping.time

import nz.net.wand.streamevmon.events.grouping.EventGroup
import nz.net.wand.streamevmon.flink.HasFlinkConfig

import java.time.{Duration, Instant}

import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

/** Groups events from a single stream into groups that occurred close to each
  * other in time.
  *
  * ==Configuration==
  *
  * This class is configured by the `eventGrouping.time` configuration key group.
  *
  * - `maximumEventLength`: The maximum duration, in seconds, that an event
  * group can span. Default 60.
  * - `maximumEventInterval`: The maximum time, in seconds, between two events
  * before a new group is started. Default 10.
  */
class TemporalEventGrouper
  extends KeyedProcessFunction[String, EventGroup, EventGroup]
          with CheckpointedFunction
          with HasFlinkConfig {

  override val flinkName: String = "Event Grouping (Temporal)"
  override val flinkUid: String = "event-grouping-temporal"
  override val configKeyGroup: String = "eventGrouping.time"

  /** The maximum duration that an event group can span */
  @transient lazy val maximumEventLength: Duration =
  Duration.ofSeconds(configWithOverride(getRuntimeContext).getLong(s"$configKeyGroup.maximumEventLength"))

  /** The maximum time between events in a single group */
  @transient lazy val maximumEventInterval: Duration =
  Duration.ofSeconds(configWithOverride(getRuntimeContext).getLong(s"$configKeyGroup.maximumEventInterval"))

  var state: MapState[String, EventGroup] = _

  override def snapshotState(context: FunctionSnapshotContext): Unit = {}

  override def initializeState(context: FunctionInitializationContext): Unit = {
    state = context
      .getKeyedStateStore
      .getMapState(new MapStateDescriptor[String, EventGroup](
        "active-events", classOf[String], classOf[EventGroup]
      ))
  }

  override def processElement(
    value: EventGroup,
    ctx  : KeyedProcessFunction[String, EventGroup, EventGroup]#Context,
    out  : Collector[EventGroup]
  ): Unit = {
    val stream = {
      val streams = value.events.map(_.stream).toSet
      if (streams.size > 1) {
        throw new IllegalArgumentException("This event grouper can only work on event groups from a single stream!")
      }
      else if (streams.isEmpty) {
        throw new IllegalArgumentException("Received an empty EventGroup!")
      }
      streams.head
    }

    // If we don't have an ongoing event for this stream, make one.
    if (!state.contains(stream)) {
      state.put(stream, value)
      ctx.timerService.registerEventTimeTimer(value.startTime.plus(maximumEventLength).toEpochMilli)
    }
    // If there is an ongoing stream, we should try merge the group we just
    // received into it.
    else {
      val entry = state.get(stream)
      val biggestTime = entry.events.maxBy(_.eventTime).eventTime

      // If the start of the new group is later than the current end of the
      // existing group, we should finalise the existing group and replace it
      // with the new group.
      if (value.startTime.isAfter(biggestTime.plus(maximumEventInterval))) {
        out.collect(state.get(ctx.getCurrentKey).copy(endTime = Some(biggestTime)))
        state.put(stream, value)
        ctx.timerService.deleteEventTimeTimer(entry.startTime.plus(maximumEventLength).toEpochMilli)
        ctx.timerService.registerEventTimeTimer(value.startTime.plus(maximumEventLength).toEpochMilli)
      }
      // Otherwise, we should merge the two groups together.
      else {
        state.put(stream, EventGroup(
          entry.startTime, None, entry.events ++ value.events
        ))
      }
    }
  }

  /** Removes the event group that triggered the timer when it becomes older
    * than `maximumEventLength`.
    */
  override def onTimer(
    timestamp: Long,
    ctx      : KeyedProcessFunction[String, EventGroup, EventGroup]#OnTimerContext,
    out      : Collector[EventGroup]
  ): Unit = {
    out.collect(state.get(ctx.getCurrentKey).copy(endTime = Some(Instant.ofEpochMilli(timestamp))))
    state.remove(ctx.getCurrentKey)
  }
}
