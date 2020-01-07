package nz.net.wand.streamevmon

import nz.net.wand.streamevmon.events.Event
import nz.net.wand.streamevmon.measurements.amp._
import nz.net.wand.streamevmon.measurements.bigdata.Flow
import nz.net.wand.streamevmon.measurements.latencyts._

import java.net.InetAddress
import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit

object SeedData {

  object icmp {

    val allExpectedMeta: Seq[ICMPMeta] = Seq(
      ICMPMeta(3, "amplet", "wand.net.nz", "ipv4", "random"),
      ICMPMeta(4, "amplet", "google.com", "ipv4", "random"),
      ICMPMeta(10, "amplet", "wand.net.nz", "ipv4", "84"),
      ICMPMeta(11, "amplet", "cloud.google.com", "ipv4", "84"),
      ICMPMeta(12, "amplet", "www.cloudflare.com", "ipv4", "84"),
      ICMPMeta(13, "amplet", "afrinic.net", "ipv4", "84"),
      ICMPMeta(14, "amplet", "download.microsoft.com", "ipv4", "84")
    )

    val subscriptionLine =
      "data_amp_icmp,stream=3 loss=0i,lossrate=0.0,median=225i,packet_size=520i,results=1i,rtts=\"[225]\" 1563761840000000000"

    val lossySubscriptionLine =
      "data_amp_icmp,stream=3 loss=1i,lossrate=1.0,packet_size=520i,results=1i,rtts=\"[None]\" 1574696840000000000"

    val lossyExpected = ICMP(
      stream = 3,
      loss = 1,
      lossrate = 1.0,
      median = None,
      packet_size = 520,
      results = 1,
      rtts = Seq(None),
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1574696840000000000L))
    )

    val expected = ICMP(
      stream = 3,
      loss = 0,
      lossrate = 0.0,
      median = Some(225),
      packet_size = 520,
      results = 1,
      rtts = Seq(Some(225)),
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1563761840000000000L))
    )

    val expectedMeta = ICMPMeta(
      stream = 3,
      source = "amplet",
      destination = "wand.net.nz",
      family = "ipv4",
      packet_size_selection = "random"
    )

    val expectedRich = RichICMP(
      stream = 3,
      source = "amplet",
      destination = "wand.net.nz",
      family = "ipv4",
      packet_size_selection = "random",
      loss = 0,
      lossrate = 0.0,
      median = Some(225),
      packet_size = 520,
      results = 1,
      rtts = Seq(Some(225)),
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1563761840000000000L))
    )
  }

  object dns {

    val allExpectedMeta: Seq[DNSMeta] = Seq(
      DNSMeta(
        1,
        source = "amplet",
        destination = "8.8.8.8",
        instance = "8.8.8.8",
        address = "8.8.8.8",
        query = "wand.net.nz",
        query_type = "AAAA",
        query_class = "IN",
        udp_payload_size = 4096,
        recurse = false,
        dnssec = false,
        nsid = false
      ),
      DNSMeta(
        2,
        source = "amplet",
        destination = "1.1.1.1",
        instance = "1.1.1.1",
        address = "1.1.1.1",
        query = "wand.net.nz",
        query_type = "AAAA",
        query_class = "IN",
        udp_payload_size = 4096,
        recurse = false,
        dnssec = false,
        nsid = false
      ),
      DNSMeta(
        15,
        source = "amplet",
        destination = "ns1.dns.net.nz",
        instance = "ns1.dns.net.nz",
        address = "202.46.190.130",
        query = "dns.net.nz",
        query_type = "NS",
        query_class = "IN",
        udp_payload_size = 4096,
        recurse = false,
        dnssec = false,
        nsid = false
      ),
      DNSMeta(
        16,
        source = "amplet",
        destination = "a.root-servers.net",
        instance = "a.root-servers.net",
        address = "198.41.0.4",
        query = "example.com",
        query_type = "NS",
        query_class = "IN",
        udp_payload_size = 4096,
        recurse = false,
        dnssec = false,
        nsid = false
      )
    )

    val subscriptionLine =
      "data_amp_dns,stream=1 flag_aa=False,flag_ad=False,flag_cd=False,flag_qr=True,flag_ra=True,flag_rd=False,flag_tc=False,lossrate=0.0,opcode=0i,query_len=40i,rcode=0i,requests=1i,response_size=68i,rtt=35799i,total_additional=1i,total_answer=1i,total_authority=0i,ttl=0i 1563761841000000000"
    val lossySubscriptionLine =
      "data_amp_dns,stream=1 lossrate=1.0,query_len=40i,requests=1i 1573020910000000000"

    val lossyExpected = DNS(
      stream = 1,
      flag_aa = None,
      flag_ad = None,
      flag_cd = None,
      flag_qr = None,
      flag_ra = None,
      flag_rd = None,
      flag_tc = None,
      lossrate = 1.0,
      opcode = None,
      query_len = 40,
      rcode = None,
      requests = 1,
      response_size = None,
      rtt = None,
      total_additional = None,
      total_answer = None,
      total_authority = None,
      ttl = None,
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1573020910000000000L))
    )

    val expected = DNS(
      stream = 1,
      flag_aa = Some(false),
      flag_ad = Some(false),
      flag_cd = Some(false),
      flag_qr = Some(true),
      flag_ra = Some(true),
      flag_rd = Some(false),
      flag_tc = Some(false),
      lossrate = 0.0,
      opcode = Some(0),
      query_len = 40,
      rcode = Some(0),
      requests = 1,
      response_size = Some(68),
      rtt = Some(35799),
      total_additional = Some(1),
      total_answer = Some(1),
      total_authority = Some(0),
      ttl = Some(0),
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1563761841000000000L))
    )

    val expectedMeta = DNSMeta(
      1,
      source = "amplet",
      destination = "8.8.8.8",
      instance = "8.8.8.8",
      address = "8.8.8.8",
      query = "wand.net.nz",
      query_type = "AAAA",
      query_class = "IN",
      udp_payload_size = 4096,
      recurse = false,
      dnssec = false,
      nsid = false
    )

    val expectedRich = RichDNS(
      stream = 1,
      source = "amplet",
      destination = "8.8.8.8",
      instance = "8.8.8.8",
      address = "8.8.8.8",
      query = "wand.net.nz",
      query_type = "AAAA",
      query_class = "IN",
      udp_payload_size = 4096,
      recurse = false,
      dnssec = false,
      nsid = false,
      flag_aa = Some(false),
      flag_ad = Some(false),
      flag_cd = Some(false),
      flag_qr = Some(true),
      flag_ra = Some(true),
      flag_rd = Some(false),
      flag_tc = Some(false),
      lossrate = 0.0,
      opcode = Some(0),
      query_len = 40,
      rcode = Some(0),
      requests = 1,
      response_size = Some(68),
      rtt = Some(35799),
      total_additional = Some(1),
      total_answer = Some(1),
      total_authority = Some(0),
      ttl = Some(0),
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1563761841000000000L))
    )
  }

  object traceroute {

    val allExpectedMeta: Seq[TracerouteMeta] = Seq(
      TracerouteMeta(5, "amplet", "google.com", "ipv4", "60"),
      TracerouteMeta(6, "amplet", "wand.net.nz", "ipv4", "60"),
      TracerouteMeta(18, "amplet", "a.root-servers.net", "ipv4", "60"),
      TracerouteMeta(19, "amplet", "afrinic.net", "ipv4", "60")
    )

    val subscriptionLine =
      "data_amp_traceroute_pathlen,stream=5 path_length=12.0 1563761842000000000"

    val expected = Traceroute(
      stream = 5,
      path_length = 12.0,
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1563761842000000000L))
    )

    val expectedMeta = TracerouteMeta(
      stream = 5,
      source = "amplet",
      destination = "google.com",
      family = "ipv4",
      packet_size_selection = "60"
    )

    val expectedRich = RichTraceroute(
      stream = 5,
      source = "amplet",
      destination = "google.com",
      family = "ipv4",
      packet_size_selection = "60",
      path_length = 12.0,
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1563761842000000000L))
    )
  }

  object tcpping {

    val subscriptionLine =
      "data_amp_tcpping,stream=9 icmperrors=0i,loss=0i,lossrate=0.0,median=189i,packet_size=64i,results=1i,rtts=\"[189]\" 1564713040000000000"

    val expected = TCPPing(
      stream = 9,
      icmperrors = 0,
      loss = 0,
      lossrate = 0.0,
      median = Some(189),
      packet_size = 64,
      results = 1,
      rtts = Seq(Some(189)),
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1564713040000000000L))
    )

    val expectedMeta = TCPPingMeta(
      stream = 9,
      source = "amplet",
      destination = "wand.net.nz",
      port = 443,
      family = "ipv4",
      packet_size_selection = "64"
    )

    val expectedRich = RichTCPPing(
      stream = 9,
      source = "amplet",
      destination = "wand.net.nz",
      port = 443,
      family = "ipv4",
      packet_size_selection = "64",
      icmperrors = 0,
      loss = 0,
      lossrate = 0.0,
      median = Some(189),
      packet_size = 64,
      results = 1,
      rtts = Seq(Some(189)),
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1564713040000000000L))
    )
  }

  object http {

    val subscriptionLine =
      "data_amp_http,stream=17 bytes=62210i,duration=77i,object_count=8i,server_count=1i 1564713045000000000"

    val expected = HTTP(
      stream = 17,
      bytes = 62210,
      duration = 77,
      object_count = 8,
      server_count = 1,
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1564713045000000000L))
    )

    val expectedMeta = HTTPMeta(
      stream = 17,
      source = "amplet",
      destination = "https://wand.net.nz/",
      max_connections = 24,
      max_connections_per_server = 8,
      max_persistent_connections_per_server = 2,
      pipelining_max_requests = 4,
      persist = true,
      pipelining = false,
      caching = false
    )

    val expectedRich = RichHTTP(
      stream = 17,
      source = "amplet",
      destination = "https://wand.net.nz/",
      max_connections = 24,
      max_connections_per_server = 8,
      max_persistent_connections_per_server = 2,
      pipelining_max_requests = 4,
      persist = true,
      pipelining = false,
      caching = false,
      bytes = 62210,
      duration = 77,
      object_count = 8,
      server_count = 1,
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1564713045000000000L))
    )
  }

  object latencyTs {

    val ampLine = "callplus-afrinic-ipv6,1391079600,462691,0.000"

    val amp = LatencyTSAmpICMP(
      stream = 0,
      source = "callplus",
      destination = "afrinic",
      family = "ipv6",
      time = Instant.ofEpochSecond(1391079600.toLong),
      average = 462691,
      lossrate = 0.000
    )

    val smokepingLineNoLoss =
      "afrinic.net,1380538800,452.753,0.000,451.013,451.634,451.649,451.876,451.976,452.218,452.268,452.388,452.446,452.752,452.753,453.010,453.095,453.379,453.545,453.747,454.080,456.494,456.496,456.526"

    val smokepingNoLoss = LatencyTSSmokeping(
      stream = 1,
      destination = "afrinic.net",
      family = "ipv4",
      time = Instant.ofEpochSecond(1380538800.toLong),
      median = Some(452.753),
      loss = 0,
      results = Seq(451.013, 451.634, 451.649, 451.876, 451.976, 452.218, 452.268, 452.388, 452.446,
        452.752, 452.753, 453.010, 453.095, 453.379, 453.545, 453.747, 454.080, 456.494, 456.496,
        456.526)
    )

    val smokepingLineSomeLoss =
      "afrinic.net,1385108700,462.624,10,462.022,462.132,462.248,462.318,462.361,462.624,463.236,463.273,464.07,464.38"

    val smokepingSomeLoss = LatencyTSSmokeping(
      stream = 2,
      destination = "afrinic.net",
      family = "ipv4",
      time = Instant.ofEpochSecond(1385108700.toLong),
      median = Some(462.493),
      loss = 10,
      results =
        Seq(462.022, 462.132, 462.248, 462.318, 462.361, 462.624, 463.236, 463.273, 464.07, 464.38)
    )

    val smokepingLineAllLoss = "afrinic.net,1381975500,,20"

    val smokepingAllLoss = LatencyTSSmokeping(
      stream = 3,
      destination = "afrinic.net",
      family = "ipv4",
      time = Instant.ofEpochSecond(1381975500.toLong),
      median = None,
      loss = 20,
      results = Seq()
    )

    val smokepingLineNoEntry = "afrinic.net,1385428200,,"

    val smokepingNoEntry = LatencyTSSmokeping(
      stream = 4,
      destination = "afrinic.net",
      family = "ipv4",
      time = Instant.ofEpochSecond(1385428200.toLong),
      median = None,
      loss = 20,
      results = Seq()
    )

    val smokepingLineMismatchedLoss =
      "afrinic.net,1384488300,435.381,7.000,434.960,434.970,435.090,435.099,435.100,435.110,435.122,435.130,435.274,435.366,435.381,435.444,435.800,435.802,436.056,436.230,436.240,436.400,436.410,588.667"

    val smokepingMismatchedLoss = LatencyTSSmokeping(
      stream = 5,
      destination = "afrinic.net",
      family = "ipv4",
      time = Instant.ofEpochSecond(1384488300.toLong),
      median = Some(435.374),
      loss = 0,
      results = Seq(434.960, 434.970, 435.090, 435.099, 435.100, 435.110, 435.122, 435.130, 435.274,
        435.366, 435.381, 435.444, 435.800, 435.802, 436.056, 436.230, 436.240, 436.400, 436.410,
        588.667)
    )
  }

  object event {
    val withTags: Event = Event(
      eventType = "threshold_events",
      stream = 1,
      severity = 10,
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1564713045000000000L)),
      detectionLatency = Duration.ofNanos(12345),
      description = "A test event :)",
      tags = Map(
        "type" -> "test",
        "secondTag" -> "alsoTest"
      ),
    )

    val withTagsAsString: String =
      s"""threshold_events,type=test,secondTag=alsoTest,stream=1 severity=10i,detection_latency=12345i,description="A test event :)" 1564713045000000000"""
    val withTagsAsLineProtocol: String = """type=test,secondTag=alsoTest,stream=1 severity=10i,detection_latency=12345i,description="A test event :)" 1564713045000000000"""

    val withoutTags: Event = Event(
      eventType = "changepoint_events",
      stream = 1,
      severity = 10,
      time = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1564713045000000000L)),
      detectionLatency = Duration.ofNanos(12345),
      description = "A test event :)",
      tags = Map(),
    )

    val withoutTagsAsString: String =
      s"""changepoint_events,stream=1 severity=10i,detection_latency=12345i,description="A test event :)" 1564713045000000000"""
    val withoutTagsAsLineProtocol: String = """stream=1 severity=10i,detection_latency=12345i,description="A test event :)" 1564713045000000000"""
  }

  object bigdata {
    val flowsAsLineProtocol: Seq[String] = Seq(
      """flow_statistics,capture_application=libtrace-bigdata,capture_host=libtrace-bigdata,category=ICMP,destination_ip_geohash=9ydqy0,protocol=ICMP,type=flow_interval flow_id=3i,start_ts=1578276733000i,duration=9.101833,ttfb=0.000000,source_ip="192.168.122.73",destination_ip="172.217.25.142",src_port=0i,dst_port=0i,in_bytes=560i,out_bytes=560i,destination_ip_longitude=-97.822000,destination_ip_latitude=37.751000,destination_ip_geohash_value=1i,destination_ip_country="United States" 1578276760000000000""",
      """flow_statistics,capture_application=libtrace-bigdata,capture_host=libtrace-bigdata,category=Web,destination_ip_geohash=gcpvjf,protocol=HTTP,type=flow_start flow_id=39i,start_ts=1578276761000i,duration=0.527794,ttfb=0.527794,source_ip="192.168.122.73",destination_ip="91.189.88.31",src_port=53448i,dst_port=80i,in_bytes=217i,out_bytes=2896i,destination_ip_longitude=-0.093000,destination_ip_latitude=51.516400,destination_ip_geohash_value=1i,destination_ip_city="London",destination_ip_country="United Kingdom" 1578276761000000000"""
    )

    val flowsExpected: Seq[Flow] = Seq(
      Flow(
        "libtrace-bigdata",
        "libtrace-bigdata",
        3,
        Flow.FlowType.Interval,
        "ICMP",
        "ICMP",
        Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1578276760000000000L)),
        Instant.ofEpochMilli(1578276733000L),
        None,
        9.101833,
        560,
        560,
        0.000000,
        InetAddress.getByName("192.168.122.73"),
        0,
        None,
        None,
        None,
        None,
        None,
        None,
        InetAddress.getByName("172.217.25.142"),
        0,
        None,
        Some("United States"),
        Some("9ydqy0"),
        Some(1),
        Some(37.751),
        Some(-97.822)
      ),
      Flow(
        "libtrace-bigdata",
        "libtrace-bigdata",
        39,
        Flow.FlowType.Start,
        "Web",
        "HTTP",
        Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(1578276761000000000L)),
        Instant.ofEpochMilli(1578276761000L),
        None,
        0.527794,
        217,
        2896,
        0.527794,
        InetAddress.getByName("192.168.122.73"),
        53448,
        None,
        None,
        None,
        None,
        None,
        None,
        InetAddress.getByName("91.189.88.31"),
        80,
        Some("London"),
        Some("United Kingdom"),
        Some("gcpvjf"),
        Some(1),
        Some(51.516400),
        Some(-0.093000)
      )
    )
  }

}
