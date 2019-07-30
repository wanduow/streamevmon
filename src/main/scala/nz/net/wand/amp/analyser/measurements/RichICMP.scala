package nz.net.wand.amp.analyser.measurements

import java.time.{Instant, ZoneId}

case class RichICMP(
    stream: Int,
    source: String,
    destination: String,
    family: String,
    packet_size_selection: String,
    loss: Int,
    lossrate: Double,
    median: Option[Int],
    packet_size: Int,
    results: Int,
    rtts: Seq[Int],
    time: Instant
) extends RichMeasurement {
  override def toString: String = {
    s"${ICMP.table_name}," +
      s"stream=$stream " +
      s"source=$source " +
      s"destination=$destination " +
      s"family=$family " +
      s"packet_size_selection=$packet_size_selection " +
      s"loss=$loss " +
      s"lossrate=$lossrate " +
      s"median=$median " +
      s"packet_size=$packet_size " +
      s"results=$results " +
      s"rtts=$rtts " +
      s"${time.atZone(ZoneId.systemDefault())}"
  }
}

object RichICMP extends RichMeasurementFactory {

  override def create(base: Measurement, meta: MeasurementMeta): Option[RichICMP] = {
    base match {
      case b: ICMP =>
        meta match {
          case m: ICMPMeta =>
            Some(
              RichICMP(
                m.stream,
                m.source,
                m.destination,
                m.family,
                m.packet_size,
                b.loss,
                b.lossrate,
                b.median,
                b.packet_size,
                b.results,
                b.rtts,
                b.time
              ))
          case _ => None
        }
      case _ => None
    }
  }
}