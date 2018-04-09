import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
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

    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Xfuture"
    ),
    javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
    testOptions in Test ++= Seq(
      // show full stack traces and test case durations
      Tests.Argument("-oDF")
    ),
//    resolvers += "twtmt-maven" at "http://dl.bintray.com/twtmt/maven/",
    version := "0.0.1",
    fork := true,
    parallelExecution in Test := false,
    autoCompilerPlugins := true,
    if (formatOnCompile) scalafmtOnCompile := true else scalafmtOnCompile := false
  )

  private def formatOnCompile = sys.props.get("format.on.compile") match {
    case Some("false") ⇒ false
    case _             ⇒ true
  }
}
