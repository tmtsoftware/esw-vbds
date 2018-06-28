import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.6",
    "com.lihaoyi" %%% "scalatags" % "0.6.7",
    "org.querki" %%% "jquery-facade" % "1.2",
    "com.github.japgolly.scalacss" %%% "core" % "0.5.5",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.5",
    "com.lihaoyi" %%% "upickle" % "0.6.6",
    "org.scalatest" %%% "scalatest" % "3.0.5" % "test",

    "org.akka-js" %%% "akkajsactor" % "1.2.5.13",
    "org.akka-js" %%% "akkajsactorstream" % "1.2.5.13"
  ))
}
