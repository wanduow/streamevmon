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

package nz.net.wand.streamevmon.measurements.amp2

import nz.net.wand.streamevmon.connectors.influx.LineProtocol

import java.time.Instant

/** The latency tests share a measurement type, and are disambiguated by the
  * `test` tag, which declares what type of latency test they are. This class
  * contains the shared tags and fields, while the child classes each declare
  * the unique tags of their corresponding test type.
  *
  * @see `[[LatencyDns]]`
  * @see `[[LatencyIcmp]]`
  * @see `[[LatencyTcpping]]`
  */
abstract class Latency(
  source: String,
  destination: String,
  test: String,
  time: Instant,
  dscp: String,
  family: String,
  count: Option[Long],
  error_code: Option[Long],
  error_type: Option[Long],
  icmpcode  : Option[Long],
  icmptype  : Option[Long],
  loss      : Option[Long],
  rtt       : Option[Long],
) extends Amp2Measurement {
  override val measurementName: String = Latency.measurementName

  override var defaultValue: Option[Double] = rtt.map(_.toDouble)
}

object Latency {
  val measurementName = "latency"

  /** @see [[Amp2Measurement `Amp2Measurement.createFromLineProtocol`]] */
  def create(proto               : LineProtocol): Option[Latency] = {
    if (proto.measurementName != measurementName) {
      None
    }
    else {
      proto.tags("test") match {
        case "dns" => LatencyDns.create(proto)
        case "icmp" => LatencyIcmp.create(proto)
        case "tcpping" => LatencyTcpping.create(proto)
      }
    }
  }
}
