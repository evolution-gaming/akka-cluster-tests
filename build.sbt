import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

name := "akka-cluster-tests"

organization := "com.evolutiongaming"

homepage := Some(new URL("http://github.com/evolution-gaming/akka-cluster-tests"))

startYear := Some(2017)

organizationName := "Evolution Gaming"

organizationHomepage := Some(url("http://evolutiongaming.com"))

bintrayOrganization := Some("evolutiongaming")

scalaVersion := "2.12.3"

crossScalaVersions := Seq("2.12.3", "2.11.11")

releaseCrossBuild := true

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture")

scalacOptions in (Compile,doc) ++= Seq("-no-link-warnings")

resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))

val AkkaVersion = "2.5.3"

lazy val clusterTests = (Project("akka-cluster-tests", file("."))
  settings (multiJvmSettings: _*)
  settings (fork in Test := true)
  settings (parallelExecution in Test := false)
  settings (libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % AkkaVersion % Compile,
  "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion % Compile,
  "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion % Compile,
  "com.typesafe.akka" %% "akka-contrib" % AkkaVersion % Compile,
  "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion % Compile,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion % Compile,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test,
  "com.evolutiongaming" %% "no-log4j-test" % "0.3" % Test,
  "com.evolutiongaming" %% "akka-tools" % "1.1.19",
  "com.google.code.findbugs" % "jsr305" % "3.0.2",
  "org.scalatest" %% "scalatest" % "3.0.3" % Test,
  "org.mockito" % "mockito-core" % "1.9.5" % Test)
  .map(_.excludeAll(
    ExclusionRule("log4j", "log4j"),
    ExclusionRule("org.slf4j", "slf4j-log4j12"),
    ExclusionRule("commons-logging", "commons-logging")))))
  .configs(MultiJvm)