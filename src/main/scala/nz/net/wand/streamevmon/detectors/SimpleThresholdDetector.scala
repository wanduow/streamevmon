package nz.net.wand.streamevmon.detectors

import nz.net.wand.streamevmon.events.Event
import nz.net.wand.streamevmon.measurements.amp.RichICMP
import nz.net.wand.streamevmon.measurements.traits.Measurement

import java.time.Duration

import org.apache.flink.streaming.api.scala.function.ProcessAllWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/** Very basic example of threshold detection.
  *
  * Examines [[nz.net.wand.streamevmon.measurements.amp.RichICMP RichICMP]]
  * objects, and emits events with a constant severity if the median value is
  * greater than the specified value (default 1000).
  *
  * @tparam T This class can accept any type of Measurement, but only provides
  *           output if the measurement is a RichICMP.
  */
class SimpleThresholdDetector[T <: Measurement](threshold: Int = 1000)
    extends ProcessAllWindowFunction[T, Event, TimeWindow] {

  override def process(context: Context, elements: Iterable[T], out: Collector[Event]): Unit = {
    elements
      .filter(_.isInstanceOf[RichICMP])
      .map(_.asInstanceOf[RichICMP])
      .filter(_.median.getOrElse(Int.MinValue) > threshold)
      .foreach { m =>
        out.collect(
          new Event(
            "threshold_events",
            m.stream,
            severity = 10,
            m.time,
            Duration.ZERO,
            s"Median latency was over $threshold",
            Map()
          )
        )
      }
  }
}
