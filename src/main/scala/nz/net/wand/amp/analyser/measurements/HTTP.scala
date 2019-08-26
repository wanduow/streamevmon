package nz.net.wand.amp.analyser.measurements

import java.time.{Instant, ZoneId}
import java.util.concurrent.TimeUnit

/** Represents an AMP HTTP measurement.
  *
  * @see [[HTTPMeta]]
  * @see [[RichHTTP]]
  * @see [[https://github.com/wanduow/amplet2/wiki/amp-http]]
  */
final case class HTTP(
    stream: Int,
    bytes: Int,
    duration: Int,
    object_count: Int,
    server_count: Int,
    time: Instant
) extends Measurement {
  override def toString: String = {
    s"${HTTP.table_name}," +
      s"stream=$stream " +
      s"bytes=${bytes}i," +
      s"duration=${duration}i," +
      s"object_count=${object_count}i," +
      s"server_count=${server_count}i " +
      s"${time.atZone(ZoneId.systemDefault())}"
  }
}

object HTTP extends MeasurementFactory {

  final override val table_name: String = "data_amp_http"

  override def create(subscriptionLine: String): Option[HTTP] = {
    val data = subscriptionLine.split(Array(',', ' '))
    val namedData = data.drop(1).dropRight(1)
    if (data(0) != table_name) {
      None
    }
    else {
      Some(
        HTTP(
          getNamedField(namedData, "stream").get.toInt,
          getNamedField(namedData, "bytes").get.dropRight(1).toInt,
          getNamedField(namedData, "duration").get.dropRight(1).toInt,
          getNamedField(namedData, "object_count").get.dropRight(1).toInt,
          getNamedField(namedData, "server_count").get.dropRight(1).toInt,
          Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(data.last.toLong))
        ))
    }
  }
}
