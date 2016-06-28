import scalariform.formatter.preferences._

name := """sender-app"""

val revision = sys.env.getOrElse("TRAVIS_BUILD_NUMBER", "0-SNAPSHOT")
version := s"""0.2.$revision"""

scalaVersion := "2.11.8"

enablePlugins(JavaAppPackaging)

name in Docker := "sender-app"
dockerExposedPorts := Seq(6080)
dockerBaseImage := "relateiq/oracle-java8"
dockerRepository := Some("impactua")
dockerUpdateLatest := true

libraryDependencies ++= Seq(
  "com.softwaremill.reactivekafka" %% "reactive-kafka-core" % "0.9.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.1",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.0.3",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.14",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.0.3",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.1" % "test"
)

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

fork in run := true
