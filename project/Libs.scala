import sbt._

object Libs {
  val ScalaVersion = "2.12.6"

  val `scalatest` = "org.scalatest" %% "scalatest" % "3.0.5" //Apache License 2.0
  val `scala-async` = "org.scala-lang.modules" %% "scala-async" % "0.9.7" //BSD 3-clause "New" or "Revised" License
  val `scopt` = "com.github.scopt" %% "scopt" % "3.7.0" //MIT License
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3" // GNU Lesser General Public License version 2.1
  val `akka-management-cluster-http` = "com.lightbend.akka" %% "akka-management-cluster-http" % "0.6" //N/A at the moment
//  val `spray-json` = "io.spray" %% "spray-json" % "1.3.3"
  val `boopickle` = "io.suzaku" %% "boopickle" % "1.3.0" // Apache License 2.0
  val `commons-io` = "commons-io" % "commons-io" % "2.6"  // Apache License 2.0
}

object Akka {
  val Version = "2.5.12" //all akka is Apache License 2.0
  val `akka-stream` = "com.typesafe.akka" %% "akka-stream" % Version
  val `akka-remote` = "com.typesafe.akka" %% "akka-remote" % Version
  val `akka-stream-testkit` = "com.typesafe.akka" %% "akka-stream-testkit" % Version
  val `akka-actor` = "com.typesafe.akka" %% "akka-actor" % Version
  val `akka-actor-typed` = "com.typesafe.akka" %% "akka-actor-typed" % Version
  val `akka-testkit-typed` = "com.typesafe.akka" %% "akka-testkit-typed" % Version
  val `akka-distributed-data` = "com.typesafe.akka" %% "akka-distributed-data" % Version
  val `akka-multi-node-testkit` = "com.typesafe.akka" %% "akka-multi-node-testkit" % Version
  val `akka-cluster` = "com.typesafe.akka" %% "akka-cluster" % Version
  val `akka-cluster-tools` = "com.typesafe.akka" %% "akka-cluster-tools" % Version
  val `akka-cluster-typed` = "com.typesafe.akka" %% "akka-cluster-typed" % Version
  val `akka-slf4j` = "com.typesafe.akka" %% "akka-slf4j" % Version
}

object AkkaHttp { //ApacheV2
  val Version = "10.1.1"
  val `akka-http` = "com.typesafe.akka" %% "akka-http" % Version
  val `akka-http-core` = "com.typesafe.akka" %% "akka-http-core" % Version
  val `akka-http-testkit` = "com.typesafe.akka" %% "akka-http-testkit" % Version
  val `akka-http-spray-json` = "com.typesafe.akka" %% "akka-http-spray-json" % Version
}

object Chill {
  val Version           = "0.9.2"
  val `chill-akka`      = "com.twitter" %% "chill-akka" % Version //Apache License 2.0
//  val `chill-bijection` = "com.twitter" %% "chill-bijection" % Version //Apache License 2.0
}


