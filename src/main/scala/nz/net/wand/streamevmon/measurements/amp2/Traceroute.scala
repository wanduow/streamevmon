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

case class Traceroute(
  source: String,
  destination: String,
  test       : String,
  time       : Instant,
  dscp       : String,
  family     : String,
  hop: Long,
  packet_size: Long,
  random     : Boolean,
  address    : Option[String],
  rtt        : Option[Long],
) extends Amp2Measurement {
  override val measurementName: String = Traceroute.measurementName
  override val tags: Seq[Any] = Seq(dscp, family, hop, packet_size, random)

  override var defaultValue: Option[Double] = rtt.map(_.toDouble)
}

object Traceroute {
  val measurementName = "traceroute"

  /** @see [[Amp2Measurement `Amp2Measurement.createFromLineProtocol`]] */
  def create(proto: LineProtocol): Option[Traceroute] = {
    if (proto.measurementName != measurementName) {
      None
    }
    else {
      Some(Traceroute(
        proto.tags("source"),
        proto.tags("destination"),
        proto.tags("test"),
        proto.time,
        proto.tags("dscp"),
        proto.tags("family"),
        proto.getTagAsLong("hop"),
        proto.getTagAsLong("packet_size"),
        proto.getTagAsBoolean("random"),
        proto.fields.get("address"),
        proto.getFieldAsLong("rtt")
      ))
    }
  }
}
