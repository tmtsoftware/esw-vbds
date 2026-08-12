import sbt._

object Dependencies {
  val vbdsServer = Seq(
    CSW.`csw-location-client`,
    CSW.`csw-network-utils`,
    Pekko.`pekko-stream-typed`,
    Pekko.`pekko-actor-typed`,
    Pekko.`pekko-cluster-typed`,
    Pekko.`pekko-slf4j`,
    PekkoHttp.`pekko-http`,
    PekkoHttp.`pekko-http-spray-json`,
    Libs.`scopt`,
    Libs.`boopickle`,
    Libs.`logback-classic`,

    Libs.`commons-io` % Test,
    Libs.`scalatest` % Test,
    PekkoHttp.`pekko-http-testkit` % Test,
    Pekko.`pekko-multi-node-testkit` % Test
  )

  val vbdsClient = Seq(
    CSW.`csw-location-client`,
    Pekko.`pekko-stream`,
    Pekko.`pekko-slf4j`,
    PekkoHttp.`pekko-http`,
    Libs.`scopt`,
    Libs.`logback-classic`,

    Libs.`scalatest` % Test,
    PekkoHttp.`pekko-http-testkit` % Test
  )
}