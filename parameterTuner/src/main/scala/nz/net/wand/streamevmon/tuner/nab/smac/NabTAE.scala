/* This file is part of streamevmon.
 *
 * Copyright (C) 2020-2021  The University of Waikato, Hamilton, New Zealand
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
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nz.net.wand.streamevmon.tuner.nab.smac

import nz.net.wand.streamevmon.tuner.jobs.{FailedJob, JobResult}
import nz.net.wand.streamevmon.Logging
import nz.net.wand.streamevmon.runners.unified.schema.DetectorType
import nz.net.wand.streamevmon.tuner.ConfiguredPipelineRunner
import nz.net.wand.streamevmon.tuner.nab.ScoreTarget

import java.util

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.{AbstractTargetAlgorithmEvaluator, TargetAlgorithmEvaluatorCallback, TargetAlgorithmEvaluatorRunObserver}

import scala.collection.JavaConverters._

/** Creates and evaluates runs with particular parameters. Acts as the glue
  * logic between our systems and SMAC.
  */
class NabTAE(
  detectors: Iterable[DetectorType.ValueBuilder],
  scoreTargets: Iterable[ScoreTarget.Value],
  baseOutputDir: String
) extends AbstractTargetAlgorithmEvaluator with Logging {

  /** Extracts a SMAC RunResult from our JobResult. */
  def jobResultToRunResult(jr: JobResult): AlgorithmRunResult = {
    jr match {
      case njr: SmacNabJobResult => njr.smacResult
      case FailedJob(_, _) => throw new IllegalArgumentException("Cannot convert FailedJob result to AlgorithmRunResult")
      case _ => throw new IllegalArgumentException("Unsupported JobResult type")
    }
  }

  /**
    * Evaluate a sequence of run configurations. This function has never been
    * called. See evaluateRunsAsync instead.
    *
    * @param runConfigs        a list containing zero or more run configurations to evaluate
    * @param runStatusObserver observer that will be notified of the current run status
    *
    * @return list of the exact same size as input containing the <code>AlgorithmRun</code> objects in the same order as runConfigs
    */
  override def evaluateRun(
    runConfigs       : util.List[AlgorithmRunConfiguration],
    runStatusObserver: TargetAlgorithmEvaluatorRunObserver
  ): util.List[AlgorithmRunResult] = ???

  /**
    * Evaluates the given configuration, and when complete the handler is invoked
    * <p>
    * <b>Note:</b>You are guaranteed that when this method returns your runs have been 'delivered'
    * to the eventual processor. In other words if the runs are dispatched to some external
    * processing system, you can safely shutdown after this method call completes and know that they have been
    * delivered. Additionally if the runs are already complete (for persistent TAEs), the call back is guaranteed to fire to completion <i>before</i>
    * this method is returned.
    *
    * @param runConfigs        list of zero or more run configuration to evaluate
    * @param taeCallback       handler to invoke on completion or failure
    * @param runStatusObserver observer that will be notified of the current run status
    */
  override def evaluateRunsAsync(
    runConfigs: util.List[AlgorithmRunConfiguration],
    taeCallback: TargetAlgorithmEvaluatorCallback,
    runStatusObserver: TargetAlgorithmEvaluatorRunObserver
  ): Unit = {
    val runs = runConfigs.asScala

    // Generate Jobs for our ActorSystem to run.
    runs.foreach { ru =>
      val job = SmacNabJob(
        ru,
        detectors,
        scoreTargets,
        baseOutputDir
      )

      // Add a JobResultHook that will parse results from one specific job, so
      // we can marry up the callbacks correctly. (This could be done with a
      // single hook and storing the callbacks in the SmacNabJob, but this is
      // fine)
      ConfiguredPipelineRunner.addJobResultHook {
        jr: JobResult => {
          jr match {
            case FailedJob(_, exception) =>
              taeCallback.onFailure(new RuntimeException(exception))
            case jr: SmacNabJobResult if jr.job.params.hashCode() == job.params.hashCode() =>
              val results = new util.ArrayList[AlgorithmRunResult](
                Seq(jobResultToRunResult(jr)).asJava
              )
              taeCallback.onSuccess(results)
            case _: SmacNabJobResult =>
            // We want one hook per job, since the callback is unique.
            case _ => logger.error(s"Unsupported job result $jr")
          }
        }
      }

      // Set the job running.
      ConfiguredPipelineRunner.submit(job)
    }
  }

  // We ignore the lifecycle functions and have static settings for the rest of
  // the options.

  /**
    * Notifies the TargetAlgorithmEvaluator that we are shutting down
    * <p>
    * <b>Implementation Note:</b> Depending on what the TargetAlgorithmEvaluator does this can be a noop, the only purpose
    * is to allow TargetAlgorithmEvaluators to shutdown any thread pools, that will prevent the JVM from exiting. The
    * TargetAlgorithmEvaluator may also choose to keep resources running for other reasons, and this method
    * should NOT be interpreted as requesting the TargetAlgorithmEvalutor to shutdown.
    * <p>
    * Example: If this TAE were to allow for sharing of resources between multiple independent SMAC runs, a call to this method
    * should NOT be taken as a requirement to shutdown the TAE, only that there is one less client using it. Once it recieved
    * sufficient shutdown notices, it could then decide to shutdown.
    * <p>
    * Finally, if this method throws an exception, chances are the client will not catch it and will crash.
    */
  override def notifyShutdown(): Unit = {}

  /**
    * Returns <code>true</code> if the TargetAlgorithmEvaluator run requests are final, that is
    * rerunning the same request again would give you an identical answer.
    * <p>
    * <b>Implementation Note:</b> This is primarily of use to prevent decorators from trying to
    * get a different answer if they don't like the first one (for instance retry crashing runs, etc).
    *
    * @return <code>true</code> if run answers are final
    */
  override def isRunFinal: Boolean = false

  /**
    * Returns <code>true</code> if all the runs made to the TargetAlgorithmEvaluator will be persisted.
    * <p>
    * <b>Implementation Note:</b> This is used to allow some programs to basically hand-off execution to some
    * external process, say a pool of workers, and then if re-executed can get the same answer later.
    *
    * @return <code>true</code> if runs can be retrieved externally of this currently running program
    */
  override def areRunsPersisted(): Boolean = false

  /**
    * Returns <code>true</code> if all the runs made to the TargetAlgorithmEvaluator are observable
    * <p>
    * <b>Implementation Note:</b> The notification of observers is made on a best-effort basis,
    * if this TAE just won't notify us then it should return false. This can allow for better logging
    * or experience for the user
    */
  override def areRunsObservable(): Boolean = false
}
