package nz.net.wand.streamevmon.detectors.changepoint

import nz.net.wand.streamevmon.events.ChangepointEvent
import nz.net.wand.streamevmon.measurements.Measurement
import nz.net.wand.streamevmon.Logging

import java.io.{File, PrintWriter}
import java.time.{Duration, Instant}

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.util.Collector

/** This is the main class for the changepoint detector module. See the package
  * description for a high-level description.
  *
  * @param initialDistribution The distribution that should be used as a base
  *                            when adding new measurements to the runs.
  * @tparam MeasT The type of [[nz.net.wand.streamevmon.measurements.Measurement Measurement]] we're receiving.
  * @tparam DistT The type of [[Distribution]] to model recent measurements with.
  */
class ChangepointProcessor[MeasT <: Measurement, DistT <: Distribution[MeasT]](
  initialDistribution: DistT
) extends RunLogic[MeasT, DistT] with Logging {

  //region Configurable options

  /** The maximum number of runs to retain. */
  protected override val maxHistory: Int = 20

  /** The number of similar consecutive outliers that must be observed before
    * the measurements are considered to be an event. Higher numbers will
    * increase detection latency.
    */
  private val changepointTriggerCount = 10

  /** If an outlier value is followed by more than this many normal values,
    * we should ignore the outlier. Small values of this option protect against
    * small numbers of outliers, such as momentary large increases in latency.
    */
  private val ignoreOutlierAfterNormalMeasurementCount = 1

  /** If there are no measurements for this many seconds, we should drop all
    * our data and start again, since the old data is no longer useful.
    */
  private val inactivityPurgeTime = Duration.ofSeconds(60)

  /** There must be at least this long between emitting events. */
  private val minimumEventInterval = Duration.ofSeconds(10)

  /** If the proportional (percentage) change in latency is greater than this
    * number, an event will be emitted. A larger number will cause smaller
    * changes to be ignored.
    */
  private val severityThreshold = 30

  //endregion

  /** The current runs that reflect a set of rolling distribution models of the
    * recently observed measurements. For example, if DistT is a normal
    * distribution, the runs would contain averages and variances.
    *
    * A run also contains a probability and a start time.
    */
  private var currentRuns: Seq[Run] = Seq()

  /** The state of currentRuns at the time immediately before anomalous
    * measurements start to roll in. This is used to get the value of the "old
    * normal" when checking to see if a sufficiently significant event has
    * occurred. It's also used to restore the runs when a lonely outlier is
    * detected.
    */
  private var normalRuns: Seq[Run] = Seq()

  /** A persistent fake run made from attributes of a couple of values from
    * normalRuns. Used as the comparison to see if the event is significant
    * enough.
    */
  private var compositeOldNormal: Run = _

  /** The last measurement we observed. Used to clear all our data if it's been
    * too long since the last measurement.
    */
  private var lastObserved: MeasT = _

  /** The last time an event was emitted. Used to stop us from throwing out a
    * whole bunch of events in a row if the data is going crazy.
    */
  private var lastEventTime: Option[Instant] = None

  /** The number of data points in a row that don't fit the expected data.
    * If this gets higher than changepointTriggerCount, an event might happen.
    */
  private var consecutiveAnomalies: Int = _

  /** The number of normal values that have happened after an outlier. If this
    * gets higher than ignoreOutlierAfterNormalMeasurementCount, the runs are
    * reset to how they were before that outlier, and the outlier is dropped.
    */
  private var consecutiveNormalAfterOutlier: Int = _

  /** The run index that was the most likely match for the last measurement.
    * Used to check if the most likely run has changed.
    */
  private var previousMostLikelyIndex: Int = _

  /** Resets the detector to a clean state.
    *
    * @param firstItem The first measurement of the clean state.
    */
  def reset(firstItem: MeasT): Unit = {
    currentRuns = Seq()
    normalRuns = Seq()

    consecutiveAnomalies = 0
    consecutiveNormalAfterOutlier = 0
    previousMostLikelyIndex = 0

    lastObserved = firstItem

    magicFlagOfGraphing = true
  }

  /** Generates a new run for a particular measurement. This should only be
    * called once per measurement, since the calculations in .withPoint could
    * be time-consuming.
    */
  override protected def newRunFor(value: MeasT, probability: Double): Run = {
    Run(
      initialDistribution.withPoint(value, 1),
      probability,
      value.time
    )
  }

  /** Determines the severity of a changepoint event. If the severity is higher
    * than the specified threshold, it is emitted, otherwise ignored.
    *
    * @param oldNormal A run representing the state of measurements before the
    *                  changepoint.
    * @param newNormal A run representing measurements after the changepoint.
    *
    * @return A value from 0-100 representing the severity of the changepoint.
    */
  def getSeverity(oldNormal: Run, newNormal: Run): Int = {
    val absDiff = Math.abs(oldNormal.dist.mean - newNormal.dist.mean)
    val relativeDiff = absDiff / Math.min(oldNormal.dist.mean, newNormal.dist.mean)
    val normalRelDiff = if (relativeDiff > 1.0) {
      1 - (1 / relativeDiff)
    }
    else {
      relativeDiff
    }
    (normalRelDiff * 100).toInt
  }

  /** Outputs an event if it has been sufficiently long since the last one.
    *
    * @param out       The collector to output the event to.
    * @param oldNormal The run representing the state of measurements before the
    *                  changepoint.
    * @param newNormal The run representing measurements after the changepoint.
    * @param value     The most recent measurement, which is after the changepoint.
    * @param severity  The severity of the event as returned by getSeverity.
    */
  def newEvent(out: Collector[ChangepointEvent],
               oldNormal: Run,
               newNormal: Run,
               value: MeasT,
               severity: Int
  ): Unit = {
    if (Duration
      .between(lastEventTime.getOrElse(Instant.EPOCH), value.time)
      .compareTo(minimumEventInterval) > 0) {

      lastEventTime = Some(value.time)

      out.collect(
        ChangepointEvent(
          Map("type" -> "changepoint"),
          value.stream,
          severity,
          oldNormal.start,
          value.time.toEpochMilli - oldNormal.start.toEpochMilli,
          s"Latency ${
            if (oldNormal.dist.mean > newNormal.dist.mean) {
              "decreased"
            }
            else {
              "increased"
            }
          } from ${oldNormal.dist.mean.toInt} to ${newNormal.dist.mean.toInt}"
        ))
    }
  }

  /** Processes a new measurement, producing zero or one events. This function
    * is called by Flink, and is the entrypoint to the detector.
    *
    * @param value The new measurement.
    * @param out   The collector to submit new events to.
    */
  def processElement(
      value: MeasT,
      out: Collector[ChangepointEvent]
  ): Unit = {

    // If this is the first item observed, we start from fresh.
    // If it's been a while since our last measurement, our old runs probably
    // aren't much use anymore, so we should start over here as well.
    if (lastObserved == null ||
        Duration
          .between(lastObserved.time, value.time)
          .compareTo(inactivityPurgeTime) > 0) {
      reset(value)
      return
    }

    // Update the last observed value if it was the most recent one seen.
    // Out of order events will otherwise be processed as though they were the
    // most recent event, which may not be what we want!
    // TODO: Investigate out of order behaviour.
    if (!Duration.between(lastObserved.time, value.time).isNegative) {
      lastObserved = value
    }

    // If we're in a normal state, we'll update the note of our normal-state
    // runs. We'll also make a composite of the most recent time and the
    // mean with the most data in it in case there's a changepoint after this,
    // so we can use the nicest numbers for determining if it's different enough
    // and to show the end-user.
    if (consecutiveAnomalies == 0) {
      normalRuns = currentRuns.copy
      compositeOldNormal = if (normalRuns.nonEmpty) {
        Run(
          normalRuns(normalRuns.filteredMaxBy(_.dist.n)).dist,
          -2.0,
          normalRuns(previousMostLikelyIndex).start
        )
      }
      else {
        Run(initialDistribution, -1.0, Instant.EPOCH)
      }
    }

    // Process the new value: Create a new run, adjust the current runs, and
    // update the probabilities of the runs depending on the new measurement.
    currentRuns = currentRuns.update(value)

    // This should only trigger if the squashing algorithm is messed up, and
    // shouldn't happen. It's here for safety.
    if (currentRuns.isEmpty) {
      logger.error("There were no runs left after applying growth probabilities! Resetting detector.")
      reset(value)
      return
    }

    // Find the most likely run, discounting the newly added run.
    val mostLikelyIndex = currentRuns.filteredMaxBy(_.prob)

    // If the most likely run has changed, then the measurement doesn't match
    // our current model of the recent 'normal' behaviour of the measurements.
    // We'll update a counter to see if this happens for a while.
    if (mostLikelyIndex != previousMostLikelyIndex) {
      consecutiveAnomalies += 1

      // This metric can determine if the measurements have returned to normal
      // after a small number of outliers. If they have, we return the detector
      // state to how it was before the outlier occurred and disregard
      // outlier measurements. The current measurement, which follows
      // our normal trend, is included.
      val highestPdf = currentRuns.filteredMaxBy(_.dist.pdf(value))

      if (highestPdf == currentRuns.length - 2) {
        consecutiveNormalAfterOutlier += 1

        if (consecutiveNormalAfterOutlier > ignoreOutlierAfterNormalMeasurementCount) {
          consecutiveAnomalies = 0
          consecutiveNormalAfterOutlier = 0
          currentRuns = normalRuns.update(value)

          writeState(value, mostLikelyIndex)
          return
        }
      }
      else {
        consecutiveNormalAfterOutlier = 0
      }
    }
    else {
      consecutiveAnomalies = 0
      consecutiveNormalAfterOutlier = 0
    }

    // Save which run was most likely so it can be compared next iteration.
    previousMostLikelyIndex = mostLikelyIndex

    // If we've had too many 'abnormal' measurements in a row, we might need to
    // emit an event. We'll also reset to a fresh state, since most of the old
    // runs have a lot of data from before the changepoint.
    // TODO: Are there any runs that only have data from after the changepoint?
    // This would help us get regenerate our maxHistory a bit quicker.
    if (consecutiveAnomalies > changepointTriggerCount) {
      val newNormal = currentRuns.filter(_.dist.n == 1).head
      val severity = getSeverity(compositeOldNormal, newNormal)
      if (severity > severityThreshold) {
        newEvent(out, compositeOldNormal, newNormal, value, severity)
        reset(value)
      }
      consecutiveAnomalies = 0

      // ==== From here down is solely used for graphing
      magicFlagOfGraphing = false
    }

    writeState(value, mostLikelyIndex)
    magicFlagOfGraphing = true
  }

  /** This magical flag goes low when we're checking if a significant event
    * has happened. It's solely used for graphs.
    */
  private var magicFlagOfGraphing: Boolean = true

  def open(config: ParameterTool): Unit = {
    writer.write("NewEntry,")
    writer.write("PrevNormalMax,")
    writer.write("CurrentMaxI,")
    writer.write("NormalIsCurrent,")
    writer.write("ConsecutiveAnomalies,")
    writer.write("ConsecutiveOutliers,")
    (0 to maxHistory).foreach(i => writer.write(s"prob$i,n$i,mean$i,var$i,"))
    writer.println()
    writer.flush()

    writerPdf.write("NewEntry,")
    (0 to maxHistory).foreach(i => writerPdf.write(s"pdf$i,"))
    writerPdf.println()
    writerPdf.flush()
  }

  private def writeState(value: MeasT, mostLikelyIndex: Int): Unit = {
    val formatter: Run => String = x => s"${x.prob},${x.dist.n},${x.dist.mean},${x.dist.variance}"

    writer.print(s"${initialDistribution.asInstanceOf[NormalDistribution[MeasT]].mapFunction(value)},")
    writer.print(s"$previousMostLikelyIndex,")
    writer.print(s"$mostLikelyIndex,")
    writer.print(s"$magicFlagOfGraphing,")
    writer.print(s"$consecutiveAnomalies,")
    writer.print(s"$consecutiveNormalAfterOutlier,")
    currentRuns.foreach(x => writer.print(s"${formatter(x)},"))
    writer.println()
    writer.flush()

    writerPdf.print(s"${initialDistribution.asInstanceOf[NormalDistribution[MeasT]].mapFunction(value)},")
    currentRuns.foreach(x => writerPdf.print(s"${x.dist.pdf(value)},"))
    writerPdf.println()
    writerPdf.flush()
  }

  private val getFile = "Normalise-Squash"
  private val writer = new PrintWriter(new File(s"../plot-ltsi/processed/$getFile.csv"))
  private val writerPdf = new PrintWriter(new File(s"../plot-ltsi/processed/pdf/$getFile-pdf.csv"))
}
