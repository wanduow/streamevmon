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

package nz.net.wand.streamevmon.measurements.traits

import nz.net.wand.streamevmon.connectors.postgres.PostgresConnection
import nz.net.wand.streamevmon.measurements.amp._
import nz.net.wand.streamevmon.measurements.bigdata.Flow

import org.squeryl.annotations.Column

import scala.annotation.tailrec
import scala.reflect.runtime.universe._
import scala.util.control.Breaks._

/** Mixed into companion objects of concrete [[InfluxMeasurement]] classes.
  * Provides helper functions for common requirements to generate objects.
  *
  * @see [[RichInfluxMeasurementFactory]]
  */
trait InfluxMeasurementFactory {

  /** The name of the InfluxDB table corresponding to a measurement type.
    */
  val table_name: String

  /** Returns the overrides declared via @Column annotations to case class field
    * names. The first item of each tuple is the field name, and the second
    * element is the value placed in the annotation.
    *
    * @tparam T The type to determine the overrides for.
    */
  private def getColumnNameOverrides[T <: InfluxMeasurement : TypeTag]: Seq[(String, String)] =
    symbolOf[T].toType.members.flatMap { m =>
      if (m.annotations.exists(a => a.tree.tpe <:< typeOf[Column])) {
        Seq((
          m.name.toString.trim,
          m
            .annotations
            .find(a => a.tree.tpe <:< typeOf[Column])
            .get
            .tree.children.tail.head
            .collect { case Literal(Constant(value: String)) => value }
            .head
        ))
      }
      else {
        Seq()
      }
    }.asInstanceOf[Seq[(String, String)]]

  /** Returns a collection containing the database column names associated with
    * a type, in the same order as the case class declares them.
    */
  protected def getColumnNames[T <: InfluxMeasurement : TypeTag]: Seq[String] = {
    val overrides = getColumnNameOverrides[T]

    "time" +: typeOf[T].members.sorted.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.name.toString
    }.filterNot(_ == "time").map { f =>
      val ov = overrides.find(_._1 == f)
      if (ov.isDefined) {
        ov.get._2
      }
      else {
        f
      }
    }
  }

  /** Returns a collection containing the database column names associated with
    * this type, in the same order as they are declared.
    */
  def columnNames: Seq[String]

  /** Searches a group of 'key=value' pairs for a particular key, and returns the
    * value.
    *
    * @param fields The group of 'key=value' pairs.
    * @param name   The key to find.
    *
    * @return The value if found, or None. If several are found, returns the first.
    */
  protected def getNamedField(fields: Iterable[String], name: String): Option[String] = {
    fields
      .filter(entry => name == entry.split('=')(0))
      .map(entry => entry.split('=')(1))
      .headOption
  }

  /** Like string.split(), but it ignores separators that are inside double quotes.
    *
    * @param line              The line to split.
    * @param precedingElements The newly discovered elements are appended to this.
    * @param separators        The separators to split on.
    */
  @tailrec
  final protected def splitLineProtocol(
    line             : String,
    precedingElements: Seq[String] = Seq(),
    separators       : Seq[Char] = Seq(',', ' ')
  ): Seq[String] = {
    var splitPoint = -1
    var quoteCount = 0
    // Iterate through the string, looking for separators.
    // Separators that are in quotes don't count, since they're part of a value.
    // We stop when we find the first one.
    breakable {
      for (i <- Range(0, line.length)) {
        if (line(i) == '"') {
          quoteCount += 1
        }
        if (quoteCount % 2 == 0 && separators.contains(line(i))) {
          splitPoint = i
          break
        }
      }
    }

    // If there aren't any left, we'll complete the seq and return it.
    if (splitPoint == -1) {
      precedingElements :+ line
    }
    // Otherwise, we'll split around the separator and give the rest to the
    // recursive function.
    else {
      val beforeSplit = line.substring(0, splitPoint)
      val afterSplit = line.substring(splitPoint + 1)
      splitLineProtocol(afterSplit, precedingElements :+ beforeSplit, separators)
    }
  }

  /** Creates an InfluxMeasurement from an InfluxDB subscription result, in Line Protocol format.
    *
    * @param subscriptionLine The line received from the subscription.
    *
    * @return The Measurement object, or None if the creation failed.
    *
    * @see [[https://docs.influxdata.com/influxdb/v1.7/write_protocols/line_protocol_reference/]]
    */
  def create(subscriptionLine: String): Option[InfluxMeasurement]

  /** Converts the "rtts" field, used in a number of AMP measurements, into an
    * appropriate datatype.
    *
    * @param in The value of the "rtts" field.
    *
    * @return A sequence of round-trip times.
    */
  protected def getRtts(in: String): Seq[Option[Int]] = {
    val dropEdges = in.drop(2).dropRight(2)
    if (dropEdges.isEmpty) {
      Seq()
    }
    else {
      dropEdges.split(',').map { x =>
        val y = x.trim
        if (y == "None") {
          None
        }
        else {
          Some(y.toInt)
        }
      }
    }
  }
}

/** Creates [[InfluxMeasurement]] and [[RichInfluxMeasurement]] objects.
  *
  * @see [[InfluxMeasurementFactory]]
  * @see [[RichInfluxMeasurementFactory]]
  */
object InfluxMeasurementFactory {

  /** Creates a Measurement from a string in InfluxDB Line Protocol format.
    *
    * @param line The string describing the measurement.
    *
    * @return The measurement if successful, or None.
    */
  def createMeasurement(line: String): Option[InfluxMeasurement] = {
    line match {
      case x if x.startsWith(ICMP.table_name) => ICMP.create(x)
      case x if x.startsWith(DNS.table_name) => DNS.create(x)
      case x if x.startsWith(TraceroutePathlen.table_name) => TraceroutePathlen.create(x)
      case x if x.startsWith(TCPPing.table_name) => TCPPing.create(x)
      case x if x.startsWith(HTTP.table_name) => HTTP.create(x)
      case x if x.startsWith(Flow.table_name) => Flow.create(x)
      case _ => None
    }
  }

  /** Enriches a measurement.
    *
    * @param pgConnection A connection to a PostgreSQL server containing
    *                     metadata for the measurement.
    * @param base         The InfluxMeasurement to enrich.
    *
    * @return The RichInfluxMeasurement if enrichment was successful, otherwise None.
    */
  def enrichMeasurement(
    pgConnection: PostgresConnection,
    base: InfluxMeasurement
  ): Option[RichInfluxMeasurement] = {
    pgConnection.getMeta(base) match {
      case Some(x) =>
        x match {
          case y: ICMPMeta => RichICMP.create(base, y)
          case y: DNSMeta => RichDNS.create(base, y)
          case y: TracerouteMeta => RichTraceroutePathlen.create(base, y)
          case y: TCPPingMeta => RichTCPPing.create(base, y)
          case y: HTTPMeta => RichHTTP.create(base, y)
          case _ => None
        }
      case None => None
    }
  }

  /** Creates a RichInfluxMeasurement directly from a string in InfluxDB Line Protocol format.
    *
    * @param pgConnection A connection to a PostgreSQL server containing
    *                     metadata for the measurement.
    * @param line         The string describing the measurement.
    *
    * @return The RichInfluxMeasurement if both measurement creation and enrichment
    *         were successful, otherwise None.
    */
  def createRichMeasurement(
    pgConnection: PostgresConnection,
    line        : String
  ): Option[RichInfluxMeasurement] = {
    createMeasurement(line).flatMap(enrichMeasurement(pgConnection, _))
  }
}
