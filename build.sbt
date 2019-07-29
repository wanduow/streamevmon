ThisBuild / resolvers ++= Seq(
    "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
    Resolver.mavenLocal
)

name := "Flink AMP Analyser"

version := "0.1-SNAPSHOT"

organization := "nz.net.wand"

ThisBuild / scalaVersion := "2.11.12"

val flinkVersion = "1.8.1"
val flinkDependencies = Seq(
  "org.apache.flink" %% "flink-scala" % flinkVersion,
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion,
  "org.apache.flink" %% "flink-table-api-scala" % flinkVersion)

val chroniclerVersion = "0.5.1"
val influxDependencies = Seq(
  "com.github.fsanaulla" %% "chronicler-ahc-io" % chroniclerVersion,
  "com.github.fsanaulla" %% "chronicler-ahc-management" % chroniclerVersion,
  "com.github.fsanaulla" %% "chronicler-macros" % chroniclerVersion
)

val postgresDependencies = Seq(
  "org.postgresql" % "postgresql" % "9.4.1208",
  "org.squeryl" %% "squeryl" % "0.9.9"
)

val scalaCacheVersion = "0.28.0"
val cacheDependencies = Seq(
  "com.github.cb372" %% "scalacache-core" % scalaCacheVersion,
  "com.github.cb372" %% "scalacache-caffeine" % scalaCacheVersion
)

val logDependencies = Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.9"
)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.testcontainers" % "postgresql" % "1.12.0" % "test",
  "com.dimafeng" %% "testcontainers-scala" % "0.29.0" % "test"
)

lazy val root = (project in file(".")).
  settings(
    libraryDependencies ++=
      flinkDependencies ++
      influxDependencies ++
      postgresDependencies ++
      cacheDependencies ++
      logDependencies ++
      testDependencies
  )

// make run command include the provided dependencies
Compile / run := Defaults.runTask(Compile / run / fullClasspath,
                                   Compile / run / mainClass,
                                   Compile / run / runner
                                  ).evaluated

// stays inside the sbt console when we press "ctrl-c" while a Flink programme executes with "run" or "runMain"
Compile / run / fork := true
Global / cancelable := true

// exclude Scala library from assembly
assembly / assemblyOption  := (assembly / assemblyOption).value.copy(includeScala = false)
