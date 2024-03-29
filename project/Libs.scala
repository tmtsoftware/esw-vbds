import sbt._

object Libs {
  val ScalaVersion = "2.13.8"

  val `scalatest` = "org.scalatest" %% "scalatest" % "3.2.11" //Apache License 2.0
  val `scala-async` = "org.scala-lang.modules" %% "scala-async" % "1.0.1" //BSD 3-clause "New" or "Revised" License
  val `scopt` = "com.github.scopt" %% "scopt" % "4.0.1" //MIT License
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.10" // GNU Lesser General Public License version 2.1
  val `akka-management-cluster-http` = "com.lightbend.akka" %% "akka-management-cluster-http" % "1.1.3" //N/A at the moment
  val `boopickle` = "io.suzaku" %% "boopickle" % "1.4.0" // Apache License 2.0
  val `commons-io` = "commons-io" % "commons-io" % "2.11.0"  // Apache License 2.0
}

object CSW {
  private val Org = "com.github.tmtsoftware.csw"
  //  private val Version = "0.1.0-SNAPSHOT"
  private val Version = "5.0.0"

  val `csw-network-utils` = Org %% "csw-network-utils" % Version
  val `csw-location-client` = Org %% "csw-location-client" % Version
  val `csw-commons` = Org %% "csw-commons" % Version
}

object Akka {
  val Version = "2.6.18" //all akka is Apache License 2.0
  val `akka-stream` = "com.typesafe.akka" %% "akka-stream" % Version
  val `akka-stream-typed` = "com.typesafe.akka" %% "akka-stream-typed" % Version
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
  val Version = "10.2.7"
  val `akka-http` = "com.typesafe.akka" %% "akka-http" % Version
  val `akka-http-core` = "com.typesafe.akka" %% "akka-http-core" % Version
  val `akka-http-testkit` = "com.typesafe.akka" %% "akka-http-testkit" % Version
  val `akka-http-spray-json` = "com.typesafe.akka" %% "akka-http-spray-json" % Version
}

//object Chill {
//  val Version           = "0.10.0"
//  val `chill-akka`      = "com.twitter" %% "chill-akka" % Version //Apache License 2.0
//}


