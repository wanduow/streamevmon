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

package nz.net.wand.streamevmon.runners.unified.schema

/** This lets Jackson map strings in the YAML file to an enum we can use in pattern matching. */
object SourceDatatype extends Enumeration {
  // AMP
  val DNS: Value = Value("dns")
  val HTTP: Value = Value("http")
  val ICMP: Value = Value("icmp")
  val TCPPing: Value = Value("tcpping")
  val TraceroutePathlen: Value = Value("traceroutepathlen")
  // AMP2
  val External: Value = Value("external")
  val Fastping: Value = Value("fastping")
  val Amp2Http: Value = Value("http")
  val Latency: Value = Value("latency")
  val LatencyDns: Value = Value("latencydns")
  val LatencyIcmp: Value = Value("latencyicmp")
  val LatencyTcpping: Value = Value("latencytcpping")
  val Pathlen: Value = Value("pathlen")
  val Sip: Value = Value("sip")
  val Throughput: Value = Value("throughput")
  val Traceroute: Value = Value("traceroute")
  val Udpstream: Value = Value("udpstream")
  val Video: Value = Value("video")
  // Bigdata
  val Flow: Value = Value("flow")
  // Esmond
  val Failure: Value = Value("failure")
  val Histogram: Value = Value("histogram")
  val Href: Value = Value("href")
  val PacketTrace: Value = Value("packettrace")
  val Simple: Value = Value("simple")
  val Subinterval: Value = Value("subinterval")
  // Latency TS
  val LatencyTSAmp: Value = Value("ampicmp")
  val LatencyTSSmokeping: Value = Value("smokeping")
  // NAB
  val NAB: Value = Value("nab")
}
