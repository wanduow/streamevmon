package nz.net.wand.streamevmon.measurements

import nz.net.wand.streamevmon.{PostgresContainerSpec, SeedData}
import nz.net.wand.streamevmon.measurements.traits.InfluxMeasurementFactory

class MeasurementEnrichTest extends PostgresContainerSpec {
  "Children of Measurement.enrich" should {

    lazy val pg = getPostgres

    "obtain the correct RichICMP object" in {
      InfluxMeasurementFactory.enrichMeasurement(pg, SeedData.icmp.expected) shouldBe Some(SeedData.icmp.expectedRich)
    }

    "obtain the correct RichDNS object" in {
      InfluxMeasurementFactory.enrichMeasurement(pg, SeedData.dns.expected) shouldBe Some(SeedData.dns.expectedRich)
    }

    "obtain the correct RichTraceroutePathlen object" in {
      InfluxMeasurementFactory.enrichMeasurement(pg, SeedData.traceroutePathlen.expected) shouldBe Some(SeedData.traceroutePathlen.expectedRich)
    }

    "obtain the correct RichTcpping object" in {
      InfluxMeasurementFactory.enrichMeasurement(pg, SeedData.tcpping.expected) shouldBe Some(SeedData.tcpping.expectedRich)
    }

    "obtain the correct RichHTTP object" in {
      InfluxMeasurementFactory.enrichMeasurement(pg, SeedData.http.expected) shouldBe Some(SeedData.http.expectedRich)
    }
  }
}
