package nz.net.wand.streamevmon.tuner.strategies

import nz.net.wand.streamevmon.parameters.{ParameterInstance, Parameters, ParameterSpec}
import nz.net.wand.streamevmon.tuner.jobs.JobResult

/** Generates random values for all parameters, except those with values set in
  * `fixedParameterValues`.
  */
case class RandomSearch(
  parameters: Seq[ParameterSpec[Any]],
  fixedParameterValues: Map[String, Any] = Map()
) extends SearchStrategy {
  override def nextParameters(lastResults: JobResult*): Parameters = {
    val newParams: Seq[ParameterInstance[Any]] = parameters
      .map { p =>
        if (fixedParameterValues.contains(p.name)) {
          ParameterInstance(p, fixedParameterValues(p.name))
        }
        else {
          p.generateRandomInRange()
        }
      }
    new Parameters(newParams: _*)
  }
}