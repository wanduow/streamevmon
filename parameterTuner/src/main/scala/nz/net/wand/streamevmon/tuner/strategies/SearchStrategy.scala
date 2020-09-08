package nz.net.wand.streamevmon.tuner.strategies

import nz.net.wand.streamevmon.Logging
import nz.net.wand.streamevmon.parameters.Parameters
import nz.net.wand.streamevmon.tuner.jobs.JobResult

abstract class SearchStrategy extends Logging {

  protected var initialParameters: Option[Parameters] = None

  def setInitialParameters(params: Parameters): Unit = {
    initialParameters = Some(params)
  }

  def nextParameters(lastResults: JobResult*): Parameters
}