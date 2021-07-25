import sbt._

val optStage = if (sys.env.contains("SCALAJS_PROD")) FullOptStage else FastOptStage
def toPathMapping(f: File): (File, String) = f -> f.getName

lazy val `vbds-server` = project
  .enablePlugins(DeployApp, VbdsBuildInfo, AutoMultiJvm)
  .settings(
    libraryDependencies ++= Dependencies.vbdsServer
  )
  .dependsOn(`vbds-client` % "test")

lazy val `vbds-client` = project
  .enablePlugins(DeployApp, VbdsBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.vbdsClient
  )

// ScalaJS client JavaScript dependencies
val clientJsDeps = Def.setting(
  Seq(
    "org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js"
  )
)


lazy val `web-client` = (project in file("web/vbds-scala-js"))
  .settings(
    scalaJSUseMainModuleInitializer := false,
    scalaJSStage := optStage,
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    packageJSDependencies / skip := false,
    jsDependencies ++= clientJsDeps.value,
    libraryDependencies ++= Dependencies.webClient.value,
    Compile / fastLinkJS / jsMappings += toPathMapping((Compile / packageJSDependencies).value),
    Compile / fullLinkJS / jsMappings += toPathMapping((Compile / packageMinifiedJSDependencies).value),
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb, JSDependenciesPlugin)
