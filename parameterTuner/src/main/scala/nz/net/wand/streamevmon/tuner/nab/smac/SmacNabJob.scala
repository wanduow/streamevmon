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

import nz.net.wand.streamevmon.parameters.{HasParameterSpecs, ParameterInstance, Parameters}
import nz.net.wand.streamevmon.runners.unified.schema.DetectorType
import nz.net.wand.streamevmon.tuner.jobs.JobResult
import nz.net.wand.streamevmon.tuner.nab.{NabJob, ScoreTarget}

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus

import scala.collection.JavaConverters._

/** We add a little extra logic to the NabJob to make it work with SMAC. */
class SmacNabJob(
  runConfig: AlgorithmRunConfiguration,
  params: Parameters,
  detectors: Iterable[DetectorType.ValueBuilder],
  optimiseFor: Iterable[ScoreTarget.Value],
  baseOutputDir: String
) extends NabJob(
  params,
  s"$baseOutputDir/${params.hashCode.toString}",
  detectors
) {

  /** SMAC needs a bit more detail in its AlgorithmRunResult, so we construct
    * one as part of a JobResult.
    */
  override protected def getResult(
    results      : Map[String, Map[String, Double]],
    runtime      : Double,
    wallClockTime: Double
  ): SmacNabJobResult = {

    // To calculate the score to be used in quality optimisation, we only
    // extract the score targets we've been configured to use.
    val scores = detectors.flatMap { det =>
      optimiseFor.map { target =>
        results(det.toString)(target.toString)
      }
    }

    if (scores.isEmpty) {
      throw new UnsupportedOperationException("Can't get score for zero results!")
    }

    // We take the average of the scores
    val score = scores.sum / scores.size
    // and subtract the result from 100, since SMAC wants to minimise quality.
    // Note that certain score targets can have negative values, so 100 is not
    // the maximum quality.
    val quality = 100.0 - score
    new SmacNabJobResult(this, results, NabAlgorithmRunResult(
      runConfig,
      RunStatus.SAT,
      runtime,
      quality,
      outputDir,
      wallClockTime
    ))
  }

  /** We don't want our systems to treat a failed job as a failed job in a SMAC
    * environment, since SMAC will handle that for us! Instead, we construct a
    * "successful" job result which contains a CRASHED status for SMAC to notice.
    */
  override protected def onFailure(
    e            : Exception,
    runtime      : Double,
    wallClockTime: Double
  ): JobResult = {
    new SmacNabJobResult(this, Map(), NabAlgorithmRunResult(
      runConfig,
      RunStatus.CRASHED,
      runtime,
      // 10E6 is the default quality for CRASHED runs in SMAC. It's much higher
      // than we'll generate anywhere else.
      10E6,
      outputDir,
      wallClockTime
    ))
  }

  override val toString: String = s"SmacNabJob-${params.hashCode.toString}"
}

object SmacNabJob {
  /** Creates a NabJob that interfaces with SMAC. */
  def apply(
    runConfig: AlgorithmRunConfiguration,
    detectors    : Iterable[DetectorType.ValueBuilder],
    optimiseFor  : Iterable[ScoreTarget.Value],
    baseOutputDir: String
  ): SmacNabJob = {

    // ParameterConfiguration extends Map[String, String], so we can use it like one.
    val params = runConfig.getParameterConfiguration
    val paramSpecs = HasParameterSpecs.getAllDetectorParameters

    // We want to turn parameters generated by SMAC into a ParameterInstance that
    // the NabJob can understand.
    val paramsWithSpecs = params.asScala.map {
      case (k, v) =>
        // Since we had to replace .s with _s for the PCS file, we need to
        // search using that replacement.
        paramSpecs.find(_.name.replace(".", "_") == k).map { spec =>
          new ParameterInstance[Any](spec, v)
        } match {
          case Some(value) => value
          case None => throw new NoSuchElementException(s"Could not find ParameterSpec for $k!")
        }
    }

    // Now we have something the NabJob can use.
    val parameters = new Parameters(paramsWithSpecs.toSeq: _*)

    new SmacNabJob(
      runConfig,
      parameters,
      detectors,
      optimiseFor,
      baseOutputDir
    )
  }
}
