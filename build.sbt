import sbt._

val optStage                               = if (sys.env.contains("SCALAJS_PROD")) FullOptStage else FastOptStage
def toPathMapping(f: File): (File, String) = f -> f.getName

lazy val `vbds-server` = project
  .enablePlugins(SbtWeb, DeployApp, SbtTwirl, VbdsBuildInfo, AutoMultiJvm)
  .settings(
    scalaJSProjects := Seq(`web-client`),
    Assets / pipelineStages := Seq(scalaJSPipeline),
    Compile / compile := ((Compile / compile) dependsOn scalaJSPipeline).value,
    libraryDependencies ++= Dependencies.vbdsServer,
    Assets / WebKeys.packagePrefix := "public/",
    Runtime / managedClasspath += (Assets / packageBin).value
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
    "org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js",
    ProvidedJS / "js9/astroem.js",
    ProvidedJS / "js9/astroemw.js",
    ProvidedJS / "js9/js9.min.js",
    ProvidedJS / "js9/js9plugins.js",
    ProvidedJS / "js9/js9prefs.js",
    ProvidedJS / "js9/js9support.min.js",
    ProvidedJS / "js9/js9worker.js"
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
