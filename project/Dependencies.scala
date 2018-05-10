import sbt._

object Dependencies {

  val vbdsServer = Seq(
    Akka.`akka-stream`,
    Akka.`akka-actor`,
    Akka.`akka-cluster`,
    Akka.`akka-distributed-data`,
    Akka.`akka-slf4j`,
    Akka.`akka-remote`,
    AkkaHttp.`akka-http`,
    AkkaHttp.`akka-http-spray-json`,
    Libs.`scopt`,
    Libs.`boopickle`,
    Chill.`chill-akka`,

    Libs.`commons-io` % Test,
    Libs.`scalatest` % Test,
    AkkaHttp.`akka-http-testkit` % Test,
    Akka.`akka-multi-node-testkit` % Test
  )

  val vbdsClient = Seq(
    Akka.`akka-stream`,
    AkkaHttp.`akka-http`,
    Libs.`scopt`,

    Libs.`scalatest` % Test,
    AkkaHttp.`akka-http-testkit` % Test
  )
}
