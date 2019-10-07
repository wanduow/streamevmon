package nz.net.wand.streamevmon.connectors

import nz.net.wand.streamevmon.{Caching, Logging}
import nz.net.wand.streamevmon.measurements._

import java.sql.DriverManager

import org.apache.flink.api.java.utils.ParameterTool
import org.postgresql.util.PSQLException
import org.squeryl.{Session, SessionFactory}
import org.squeryl.adapters.PostgreSqlAdapter

import scala.concurrent.duration.{FiniteDuration, _}

/** Contains additional apply methods for the companion class.
  */
object PostgresConnection extends Caching {

  /** Creates a new PostgresConnection from the given config. Expects all fields
    * specified in the companion class' main documentation to be present.
    *
    * @param p            The configuration to use. Generally obtained from the Flink
    *                     global configuration.
    * @param configPrefix A custom config prefix to use, in case the configuration
    *                     object is not as expected.
    *
    * @return A new PostgresConnection object.
    */
  def apply(p: ParameterTool, configPrefix: String = "postgres.dataSource"): PostgresConnection = {
    PostgresConnection(
      p.get(s"$configPrefix.serverName"),
      p.getInt(s"$configPrefix.portNumber"),
      p.get(s"$configPrefix.databaseName"),
      p.get(s"$configPrefix.user"),
      p.get(s"$configPrefix.password"),
      p.getInt("caching.ttl")
    ).withMemcachedIfEnabled(p)
  }

  /** Creates a new PostgresConnection object from a JDBC URL along with the
    * username, password, and desired caching TTL.
    *
    * @return A new PostgresConnection object.
    */
  def apply(
    jdbcUrl: String,
    user: String,
    password: String,
    caching_ttl: Int
  ): PostgresConnection = {

    val parts = jdbcUrl
      .stripPrefix("jdbc:postgresql://")
      .split(Array(':', '/', '?'))

    PostgresConnection(
      parts(0),
      parts(1).toInt,
      parts(2),
      user,
      password,
      caching_ttl
    )
  }
}

/** PostgreSQL interface which produces
  * [[nz.net.wand.streamevmon.measurements.MeasurementMeta MeasurementMeta]]
  * objects.
  *
  * ==Configuration==
  *
  * This class is configured by the `postgres.dataSource` config key group.
  *
  * - `serverName`: The hostname which the PostgreSQL database can be reached on.
  * Default: "localhost"
  *
  * - `portNumber`: The port that PostgreSQL is running on.
  * Default: 5432
  *
  * - `databaseName`: The database which the metadata is stored in.
  * Default: "nntsc"
  *
  * - `user`: The username which should be used to connect to the database.
  * Default: "cuz"
  *
  * - `password`: The password which should be used to connect to the database.
  * Default: ""
  *
  * This class uses [[Caching]]. The TTL value of the cached results can be set
  * with the `caching.ttl` configuration key, which defaults to 30 seconds.
  *
  * @see [[InfluxConnection]]
  */
case class PostgresConnection(
  host: String,
  port  : Int,
  databaseName: String,
  user        : String,
  password    : String,
  caching_ttl : Int
) extends Caching
          with Logging {

  @transient private[this] lazy val ttl: Option[FiniteDuration] = {
    if (caching_ttl == 0) {
      None
    }
    else {
      Some(caching_ttl.seconds)
    }
  }

  private[this] def jdbcUrl: String = {
    s"jdbc:postgresql://$host:$port/$databaseName?loggerLevel=OFF"
  }

  private[this] def getOrInitSession(): Boolean = {
    SessionFactory.concreteFactory match {
      case Some(_) => true
      case None =>
        SessionFactory.concreteFactory = {
          try {
            DriverManager.getConnection(jdbcUrl, user, password)

            Some(() => {
              Class.forName("org.postgresql.Driver")
              Session.create(
                DriverManager.getConnection(jdbcUrl, user, password),
                new PostgreSqlAdapter
              )
            })
          }
          catch {
            case e: PSQLException =>
              logger.error(s"Could not initialise PostgreSQL session! $e")
              None
          }
        }
        SessionFactory.concreteFactory.isDefined
    }
  }

  /** Gets the metadata associated with a given measurement. Uses caching.
    *
    * @param base The measurement to gather metadata for.
    *
    * @return The metadata if successful, otherwise None.
    */
  def getMeta(base: Measurement): Option[MeasurementMeta] = {
    val cacheKey = s"${base.getClass.getSimpleName}.${base.stream}"
    val result: Option[MeasurementMeta] = getWithCache(
      cacheKey,
      ttl, {
        if (!getOrInitSession()) {
          None
        }
        else {
          import PostgresSchema._
          import SquerylEntrypoint._

          base match {
            case _: ICMP => transaction(icmpMeta.where(m => m.stream === base.stream).headOption)
            case _: DNS => transaction(dnsMeta.where(m => m.stream === base.stream).headOption)
            case _: Traceroute =>
              transaction(tracerouteMeta.where(m => m.stream === base.stream).headOption)
            case _: TCPPing =>
              transaction(tcppingMeta.where(m => m.stream === base.stream).headOption)
            case _: HTTP => transaction(httpMeta.where(m => m.stream === base.stream).headOption)
            case _ => None
          }
        }
      }
    )
    if (result.isEmpty) {
      invalidate(cacheKey)
    }
    result
  }

  /** Enables Memcached caching according to the specified configuration.
    *
    * @see [[nz.net.wand.streamevmon.Caching Caching]] for relevant config keys.
    */
  def withMemcachedIfEnabled(p: ParameterTool): PostgresConnection = {
    if (p.getBoolean("caching.memcached.enabled")) {
      useMemcached(p)
    }
    this
  }
}