package nz.net.wand.amp.analyser.flink

import nz.net.wand.amp.analyser.events.{Event, ThresholdEvent}
import nz.net.wand.amp.analyser.measurements.{Measurement, RichICMP}

import org.apache.flink.streaming.api.scala.function.ProcessAllWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

class SimpleThresholdProcessFunction[T <: Measurement]
    extends ProcessAllWindowFunction[T, Event, TimeWindow] {
  override def process(context: Context, elements: Iterable[T], out: Collector[Event]): Unit = {
    elements
      .filter(_.isInstanceOf[RichICMP])
      .map(_.asInstanceOf[RichICMP])
      .filter(_.median.getOrElse(0) > 1000)
      .foreach(
        m =>
          out.collect(
            ThresholdEvent(
              tags = Map(
                "stream" -> m.stream.toString
              ),
              severity = 10,
              time = m.time
            )
        )
      )
  }
}
