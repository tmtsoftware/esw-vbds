//import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object Common extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = JvmPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    organization := "org.tmt",
    organizationName := "TMT",
    scalaVersion := Libs.ScalaVersion,
    organizationHomepage := Some(url("http://www.tmt.org")),
    resolvers += "jitpack" at "https://jitpack.io",

    // Scala 3 (3.6.4). The following Scala 2-only flags were removed in the port:
    //   -Wdead-code, -Xlint:_,-missing-interpolator, -Xsource:3, -Xcheckinit, -Xasync
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation"
    ),
    Compile / doc / javacOptions ++= Seq("-Xdoclint:none"),
    Test / testOptions ++= Seq(
      // show full stack traces and test case durations
      Tests.Argument("-oDF")
    ),
    version := "0.0.1",
    isSnapshot := true,
//    ThisBuild / isSnapshot := true,
    Test / parallelExecution := false,
    autoCompilerPlugins := true
//    if (formatOnCompile) scalafmtOnCompile := true else scalafmtOnCompile := false
  )

//  private def formatOnCompile = sys.props.get("format.on.compile") match {
//    case Some("false") => false
//    case _             => true
//  }
}