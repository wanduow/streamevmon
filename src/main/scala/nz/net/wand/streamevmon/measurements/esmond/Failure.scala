package nz.net.wand.streamevmon.measurements.esmond

import nz.net.wand.streamevmon.connectors.esmond.schema.FailureTimeSeriesEntry
import nz.net.wand.streamevmon.measurements.CsvOutputable

import java.time.Instant

case class Failure(
  stream     : Int,
  failureText: Option[String],
  time       : Instant
) extends EsmondMeasurement
          with CsvOutputable {
  override def toCsvFormat: Seq[String] = Seq(stream, failureText, time).map(toCsvEntry)
}

object Failure {
  def apply(
    stream: Int,
    entry : FailureTimeSeriesEntry
  ): Failure = new Failure(
    stream,
    entry.failureText,
    Instant.ofEpochSecond(entry.timestamp)
  )
}