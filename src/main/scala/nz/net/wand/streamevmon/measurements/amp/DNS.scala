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

package nz.net.wand.streamevmon.measurements.amp

import nz.net.wand.streamevmon.measurements.traits.{InfluxMeasurement, InfluxMeasurementFactory}

import java.time.{Instant, ZoneId}
import java.util.concurrent.TimeUnit

/** Represents an AMP DNS measurement.
  *
  * @see [[DNSMeta]]
  * @see [[RichDNS]]
  * @see [[https://github.com/wanduow/amplet2/wiki/amp-dns]]
  */
final case class DNS(
  stream          : String,
  flag_aa         : Option[Boolean],
  flag_ad         : Option[Boolean],
  flag_cd         : Option[Boolean],
  flag_qr         : Option[Boolean],
  flag_ra         : Option[Boolean],
  flag_rd         : Option[Boolean],
  flag_tc         : Option[Boolean],
  lossrate        : Option[Double],
  opcode          : Option[Int],
  query_len       : Option[Int],
  rcode           : Option[Int],
  requests        : Int,
  response_size   : Option[Int],
  rtt             : Option[Int],
  total_additional: Option[Int],
  total_answer    : Option[Int],
  total_authority : Option[Int],
  ttl             : Option[Int],
  time            : Instant
) extends InfluxMeasurement {
  override def toString: String = {
    s"${DNS.table_name}," +
      s"stream=$stream " +
      s"flag_aa=${flag_aa.getOrElse("")}," +
      s"flag_ad=${flag_ad.getOrElse("")}," +
      s"flag_cd=${flag_cd.getOrElse("")}," +
      s"flag_qr=${flag_qr.getOrElse("")}," +
      s"flag_ra=${flag_ra.getOrElse("")}," +
      s"flag_rd=${flag_rd.getOrElse("")}," +
      s"flag_tc=${flag_tc.getOrElse("")}," +
      s"lossrate=$lossrate," +
      s"opcode=${opcode.getOrElse("")}," +
      s"query_len=${query_len.getOrElse("")}," +
      s"rcode=${rcode.getOrElse("")}," +
      s"requests=$requests," +
      s"response_size=${response_size.getOrElse("")}," +
      s"rtt=${rtt.getOrElse("")}," +
      s"total_additional=${total_additional.getOrElse("")}," +
      s"total_answer=${total_answer.getOrElse("")}," +
      s"total_authority=${total_authority.getOrElse("")}," +
      s"ttl=${ttl.getOrElse("")} " +
      s"${time.atZone(ZoneId.systemDefault())}"
  }

  override def isLossy: Boolean = lossrate.getOrElse(1.0) > 0.0

  override def toCsvFormat: Seq[String] = DNS.unapply(this).get.productIterator.toSeq.map(toCsvEntry)

  var defaultValue: Option[Double] = rtt.map(_.toDouble)
}

object DNS extends InfluxMeasurementFactory {

  final override val table_name: String = "data_amp_dns"

  override def columnNames: Seq[String] = getColumnNames[DNS]

  override def create(subscriptionLine: String): Option[DNS] = {
    val data = splitLineProtocol(subscriptionLine)
    if (data.head != table_name) {
      None
    }
    else {
      Some(
        DNS(
          getNamedField(data, "stream").get,
          getNamedField(data, "flag_aa").map(_.toBoolean),
          getNamedField(data, "flag_ad").map(_.toBoolean),
          getNamedField(data, "flag_cd").map(_.toBoolean),
          getNamedField(data, "flag_qr").map(_.toBoolean),
          getNamedField(data, "flag_ra").map(_.toBoolean),
          getNamedField(data, "flag_rd").map(_.toBoolean),
          getNamedField(data, "flag_tc").map(_.toBoolean),
          getNamedField(data, "lossrate").map(_.toDouble),
          getNamedField(data, "opcode").map(_.dropRight(1).toInt),
          getNamedField(data, "query_len").map(_.dropRight(1).toInt),
          getNamedField(data, "rcode").map(_.dropRight(1).toInt),
          getNamedField(data, "requests").get.dropRight(1).toInt,
          getNamedField(data, "response_size").map(_.dropRight(1).toInt),
          getNamedField(data, "rtt").map(_.dropRight(1).toInt),
          getNamedField(data, "total_additional").map(_.dropRight(1).toInt),
          getNamedField(data, "total_answer").map(_.dropRight(1).toInt),
          getNamedField(data, "total_authority").map(_.dropRight(1).toInt),
          getNamedField(data, "ttl").map(_.dropRight(1).toInt),
          Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(data.last.toLong))
        ))
    }
  }
}
