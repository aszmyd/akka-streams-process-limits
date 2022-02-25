name := "akka-streams-process-limits"

version := "0.1"

scalaVersion := "2.13.8"

val akkaV             = "2.6.18"

libraryDependencies ++= List(
  "com.typesafe.akka"   %% "akka-stream" % akkaV,
)
