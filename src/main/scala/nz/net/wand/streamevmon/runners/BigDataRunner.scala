package nz.net.wand.streamevmon.runners

import nz.net.wand.streamevmon.Configuration

import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.streaming.api.scala._

object BigDataRunner {

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    System.setProperty("influx.dataSource.default.subscriptionName", "BigDataRunner")

    env.getConfig.setGlobalJobParameters(Configuration.get(args))

    env.enableCheckpointing(10000, CheckpointingMode.EXACTLY_ONCE)

    env.getConfig.setGlobalJobParameters(Configuration.get(args))


  }
}