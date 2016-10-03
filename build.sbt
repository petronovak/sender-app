import com.typesafe.sbt.packager.docker.{ExecCmd, Cmd}

import scalariform.formatter.preferences._

name := """sender-app"""

val revision = sys.env.getOrElse("TRAVIS_BUILD_NUMBER", "0-SNAPSHOT")
version := s"""0.2.$revision"""

scalaVersion := "2.11.8"

enablePlugins(SbtNativePackager, JavaAppPackaging)

name in Docker := "sender-app"
dockerExposedPorts := Seq(6080)
dockerBaseImage := "anapsix/alpine-java"
dockerRepository := Some("impactua")
dockerUpdateLatest := true
daemonUser in Docker := "root"

dockerCommands ++= Seq(
  ExecCmd("RUN", "apk", "add", "--update", "libstdc++")
)

val akkaVersion = "2.4.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.12",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.4-M2",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
)

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

fork in run := true
