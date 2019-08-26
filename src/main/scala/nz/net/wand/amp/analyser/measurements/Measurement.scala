package nz.net.wand.amp.analyser.measurements

import java.time.Instant

/** Represents a single measurement at a point in time.
  */
abstract class Measurement {

  /** AMP measurements are tagged with a stream ID which corresponds to a
    * particular unique scheduled test.
    */
  val stream: Int

  /** The time at which the measurement occurred.
    */
  val time: Instant
}
