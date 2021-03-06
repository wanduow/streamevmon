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

package nz.net.wand.streamevmon.events.grouping.graph.itdk

import nz.net.wand.streamevmon.Logging
import nz.net.wand.streamevmon.connectors.postgres.schema.AsNumber

import java.io._
import java.net.{Inet4Address, Inet6Address, InetAddress}
import java.nio.ByteBuffer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.annotation.tailrec

/** Reads an ITDK dataset. Requires the output of the [[ItdkLookupPreprocessor]]
  * as it is considerably faster than reading the raw file once preprocessing is
  * complete.
  *
  * Note that this dataset only includes IPv4 addresses.
  */
class ItdkAliasLookup(alignedFile: File, lookupMapFile: File) extends Logging {
  // All IPv4 entries in our aligned format are 12 bytes wide
  @transient protected val entryLength = 12
  // If we converge on a target that we don't want, check this many entries to
  // either side of it, just in case.
  @transient protected val checkEitherSideOnConvergenceAmount = 2

  @transient protected val reader = new RandomAccessFile(alignedFile, "r")

  @transient protected val fileSize: Long = alignedFile.length
  @transient protected val numEntries: Long = fileSize / entryLength
  @transient protected val maxEntryIndex: Long = numEntries - 1

  /** The preprocessor gives us a JSON file showing the distance (0.0-1.0) that
    * each possible first octet of an Inet4Address starts at in the aligned
    * file. This reduces the search time, since we only have to search for the
    * last three octets instead of all four.
    */
  protected val lookupMap: Map[Byte, Double] = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val jsonIn = new FileReader(lookupMapFile)
    mapper
      .readValue(jsonIn, classOf[Map[String, Double]])
      .map(kv => (kv._1.toInt.toByte, kv._2))
  }

  /** Compares two addresses, as stored in an InetAddress. Comparing arrays
    * like this doesn't seem to have a sensible implementation, so this will do.
    */
  protected def compareAddresses(a: Array[Byte], b: Array[Byte]): Int = {
    @inline def bToI(byte: Byte): Int = byte & 0xff

    bToI(a(0)).compareTo(bToI(b(0))) match {
      case 0 => bToI(a(1)).compareTo(bToI(b(1))) match {
        case 0 => bToI(a(2)).compareTo(bToI(b(2))) match {
          case 0 => bToI(a(3)).compareTo(bToI(b(3)))
          case other => other
        }
        case other => other
      }
      case other => other
    }
  }

  /** Recursively searches the aligned lookup file for the given address, and
    * returns its corresponding node ID if found.
    *
    * @param lowGuess   The known lower bound of the target's location in the file.
    *                   Between 0.0-1.0
    * @param highGuess  The known upper bound of the target's location in the file.
    *                   Between 0.0-1.0
    * @param target     The Inet4Address to find. Use `getAddress`.
    * @param lastResult If the address that it retrieved from the file is the
    *                   same twice in a row, the function terminates since we
    *                   will only ever see the same address from here on.
    * @param depth      The number of times the function has called itself. For trace
    *                   logging only.
    */
  @tailrec
  protected final def search(
    lowGuess  : Double,
    highGuess : Double,
    target    : Array[Byte],
    lastResult: Option[Array[Byte]] = None,
    depth     : Int = 1
  ): Option[(Int, AsNumber)] = {
    // We just do a naive binary chop to locate the rest of the address, since
    // we only have information on the distribution of the first octet, which
    // gets dealt with outside this function.
    val midpointOfGuesses = (highGuess + lowGuess) / 2
    val result = getNthAddress((midpointOfGuesses * maxEntryIndex).toLong)

    // If the result we got is the same as the last result we found, we'll check
    // a few entries to both sides of it in case we got really close without
    // finding it.
    // If we don't find it after that, we give up.
    if (lastResult.isDefined && compareAddresses(result._1.getAddress, lastResult.get) == 0) {
      val checkEitherSideResult = Range(
        -checkEitherSideOnConvergenceAmount,
        checkEitherSideOnConvergenceAmount
      )
        .flatMap { n =>
          if (
            n < 0 && (midpointOfGuesses * maxEntryIndex) + n >= 0 ||
              n > 0 && (midpointOfGuesses * maxEntryIndex).toLong + n <= maxEntryIndex
          ) {
            val r = getNthAddress((midpointOfGuesses * maxEntryIndex).toLong + n)
            if (compareAddresses(target, r._1.getAddress) == 0) {
              Some(r._2, r._3)
            }
            else {
              None
            }
          }
          else {
            None
          }
        }
      if (checkEitherSideResult.isEmpty) {
        logger.trace(s"Failed to find result after $depth lookups because we found the same result twice")
        None
      }
      else {
        logger.trace(s"Found result after $depth lookups once checking a few either side")
        Some(checkEitherSideResult.head)
      }
    }
    else {
      // If the result is not what we expected, we know that the midpoint
      // we tried is lower or higher than the target's true location.
      val compare = compareAddresses(target, result._1.getAddress)
      compare match {
        case 0 =>
          logger.trace(s"Found result after $depth lookups")
          Some((result._2, result._3))
        case a if a < 0 => search(lowGuess, midpointOfGuesses, target, Some(result._1.getAddress), depth + 1)
        case a if a > 0 => search(midpointOfGuesses, highGuess, target, Some(result._1.getAddress), depth + 1)
      }
    }
  }

  /** Retrieves an entry from the aligned lookup file. Requires `n` to be
    * between 0 and `maxEntryIndex` inclusive.
    */
  def getNthAddress(n: Long): (InetAddress, Int, AsNumber) = {
    reader.seek(n * entryLength)
    val buf = Array.ofDim[Byte](entryLength)
    reader.readFully(buf)

    (InetAddress.getByAddress(buf.take(4)), ByteBuffer.wrap(buf.drop(4)).getInt, AsNumber(ByteBuffer.wrap(buf.drop(8)).getInt))
  }

  /** Attempts to locate a node ID from an InetAddress. */
  def getNodeFromAddress(address: InetAddress): Option[(Int, AsNumber)] = {
    address match {
      case addr: Inet4Address =>
        val lowGuess = lookupMap(addr.getAddress.head)
        val highGuess = if (addr.getAddress.head != 255.toByte) {
          lookupMap((addr.getAddress.head + 1).toByte)
        }
        else {
          1.0
        }
        search(lowGuess, highGuess, addr.getAddress)
      case _: Inet6Address =>
        logger.trace(s"Unsupported InetAddress of type ${address.getClass.getCanonicalName}")
        None
      case _ =>
        logger.error(s"Unsupported InetAddress of type ${address.getClass.getCanonicalName}")
        None
    }
  }

  /** Attempts to locate a node ID from an InetAddress. */
  def getNodeFromAddress(address: String): Option[(Int, AsNumber)] = {
    getNodeFromAddress(InetAddress.getByName(address))
  }
}

object ItdkAliasLookup {

  /** WARNING: EXPENSIVE
    *
    * Creates an ItdkLookup based on a raw ITDK dataset, using the preprocessor
    * first. If you have already run the preprocessor, do not use this function.
    *
    * @see [[ItdkLookupPreprocessor.preprocess]]
    */
  def apply(rawFile: File, cleanup: Boolean = false): ItdkAliasLookup = {
    val files = ItdkLookupPreprocessor.preprocess(rawFile, cleanup)
    new ItdkAliasLookup(files._1, files._2)
  }
}
