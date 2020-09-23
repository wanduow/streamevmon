package nz.net.wand.streamevmon.tuner.nab.smac

import nz.net.wand.streamevmon.runners.unified.schema.DetectorType
import nz.net.wand.streamevmon.tuner.nab.ScoreTarget

import java.util

import ca.ubc.cs.beta.aeatk.options.AbstractOptions
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.{TargetAlgorithmEvaluator, TargetAlgorithmEvaluatorFactory}

/** Creates NabTAE instances, which are in charge of creating and evaluating
  * runs.
  */
class NabTAEFactory extends TargetAlgorithmEvaluatorFactory {

  private lazy val detectors = System
    .getProperty("nz.net.wand.streamevmon.tuner.detectors")
    .split(",")
    .map(DetectorType.withName)
    .map(_.asInstanceOf[DetectorType.ValueBuilder])

  private lazy val scoreTargets = System
    .getProperty("nz.net.wand.streamevmon.tuner.scoreTargets")
    .split(",")
    .map(ScoreTarget.withName)

  private lazy val baseOutputDir = System.getProperty("nz.net.wand.streamevmon.tuner.runOutputDir")

  override def getName: String = "NAB"

  override def getTargetAlgorithmEvaluator: TargetAlgorithmEvaluator = new NabTAE(detectors, scoreTargets, baseOutputDir)

  override def getTargetAlgorithmEvaluator(options: AbstractOptions): TargetAlgorithmEvaluator = new NabTAE(detectors, scoreTargets, baseOutputDir)

  override def getTargetAlgorithmEvaluator(optionsMap: util.Map[String, AbstractOptions]): TargetAlgorithmEvaluator = new NabTAE(detectors, scoreTargets, baseOutputDir)

  override def getOptionObject: AbstractOptions = new AbstractOptions {}
}
