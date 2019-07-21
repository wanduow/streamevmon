package nz.net.wand

import nz.net.wand.measurements.ICMP

import com.github.fsanaulla.chronicler.ahc.io.{AhcIOClient, InfluxIO}
import com.github.fsanaulla.chronicler.core.alias.ErrorOr
import com.github.fsanaulla.chronicler.core.model.InfluxCredentials
import com.github.fsanaulla.chronicler.macros.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object InfluxConsumer {

  def doICMP(influxDB: AhcIOClient, database: String): Future[ErrorOr[Array[ICMP]]] = {

    val measurement =
      influxDB.measurement[ICMP](database, "data_amp_icmp")

    val result = measurement.read("SELECT * FROM data_amp_icmp fill(-1)")

    result.onComplete {
      case Success(qr) if qr.isRight =>
        println(s"Found ${qr.right.get.length} ICMP results")
      case Success(qr) if qr.isLeft =>
        println(s"Failed query in chronicler: $qr")
      case Failure(exception) =>
        println(s"Failed query with exception: $exception")
    }

    result
  }

  def main(args: Array[String]): Unit = {
    val influxDB =
      InfluxIO("localhost", 8086, Some(InfluxCredentials("cuz", "")))
    val database = "nntsc"

    val pingFuture = influxDB.ping
    pingFuture.onComplete {
      case Success(_) => println(s"Successfully connected to InfluxDB")
      case Failure(exception) =>
        println(s"Failed to connect: $exception")
        System.exit(1)
    }
    Await.ready(pingFuture, Duration.Inf)
    Await.result(doICMP(influxDB, database), Duration.Inf)

    influxDB.close()
  }
}
