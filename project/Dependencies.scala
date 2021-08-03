import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val vbdsServer = Seq(
    CSW.`csw-location-client`,
    CSW.`csw-network-utils`,
    Akka.`akka-stream-typed`,
    Akka.`akka-actor-typed`,
    Akka.`akka-cluster-typed`,
    Akka.`akka-slf4j`,
//    Akka.`akka-remote`,
    AkkaHttp.`akka-http`,
    AkkaHttp.`akka-http-spray-json`,
    Libs.`scopt`,
    Libs.`boopickle`,
    Chill.`chill-akka`,
    Libs.`logback-classic`,

    Libs.`commons-io` % Test,
    Libs.`scalatest` % Test,
    AkkaHttp.`akka-http-testkit` % Test,
    Akka.`akka-multi-node-testkit` % Test
  )

  val vbdsClient = Seq(
    CSW.`csw-location-client`,
    Akka.`akka-stream`,
    Akka.`akka-slf4j`,
    AkkaHttp.`akka-http`,
    Libs.`scopt`,
    Libs.`logback-classic`,

    Libs.`scalatest` % Test,
    AkkaHttp.`akka-http-testkit` % Test
  )

  // ScalaJS web client scala dependencies
  val webClient = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "1.1.0",
    "com.lihaoyi" %%% "scalatags" % "0.9.4",
//    "org.querki" %%% "jquery-facade" % "2.0",
    "com.github.japgolly.scalacss" %%% "core" % "0.7.0",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.7.0",
    "com.lihaoyi" %%% "upickle" % "1.4.0",
    "org.scalatest" %%% "scalatest" % "3.2.9" % "test"
  ))

}
