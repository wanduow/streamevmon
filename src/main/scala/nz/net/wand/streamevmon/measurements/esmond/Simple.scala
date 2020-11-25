package nz.net.wand.streamevmon.measurements.esmond

import nz.net.wand.streamevmon.connectors.esmond.schema.SimpleTimeSeriesEntry
import nz.net.wand.streamevmon.measurements.traits.{CsvOutputable, HasDefault}

import java.time.Instant

/** This type can represent quite a few esmond event types for simple time
  * series data.
  */
case class Simple(
  stream: String,
  value : Double,
  time  : Instant
) extends EsmondMeasurement
          with HasDefault
          with CsvOutputable {
  override def toCsvFormat: Seq[String] = Seq(stream, value, time).map(toCsvEntry)

  override var defaultValue: Option[Double] = Some(value)
}

object Simple {
  def apply(
    stream: String,
    entry : SimpleTimeSeriesEntry
  ): Simple = new Simple(
    stream,
    entry.value,
    Instant.ofEpochSecond(entry.timestamp)
  )
}
