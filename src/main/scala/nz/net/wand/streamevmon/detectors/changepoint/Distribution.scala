package nz.net.wand.streamevmon.detectors.changepoint

/** A trait mixed into classes representing continuous probability distributions
  * that evolve as more data is provided to them.
  *
  * @tparam T The type of object that the implementing class can accept as a
  *           new point to add to the model. Should be the same as the MeasT of
  *           [[ChangepointDetector]].
  */
trait Distribution[T] {

  /** A friendly name for this distribution. */
  val distributionName: String

  /** The function to apply to elements of type T to obtain the relevant data
    * for this distribution. For example, an ICMP measurement would most likely
    * have its latency value extracted.
    */
  val mapFunction: T => Double

  /** The probability density function, returning the relative likelihood for a
    * continuous random variable to take the value that arises after applying
    * mapFunction to x.
    *
    * [[https://en.wikipedia.org/wiki/Probability_density_function]]
    */
  def pdf(x: T): Double

  /** Returns a new Distribution after adjustment for the new point added to it. */
  def withPoint(p: T, newN: Int): Distribution[T]

  val mean: Double
  val variance: Double
  val n: Int
}