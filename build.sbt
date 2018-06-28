
lazy val `vbds-server` = project
  .enablePlugins(DeployApp, VbdsBuildInfo, AutoMultiJvm)
  .settings(
    libraryDependencies ++= Dependencies.vbdsServer
  ).dependsOn(`vbds-client` % "test")

lazy val `vbds-client` = project
  .enablePlugins(DeployApp, VbdsBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.vbdsClient
  )

lazy val `web-client` = project.in(file("web/vbds-scala-js"))
  .enablePlugins(ScalaJSBundlerPlugin, WorkbenchPlugin)
  .settings(
    npmDependencies in Compile ++= Seq(
      //      "jquery" -> "3.3.1",
      //      "bootstrap" -> "4.1.1",
      "js9" -> "2.1.0"
    ),
    libraryDependencies ++= Dependencies.webClient.value,
    skip in packageJSDependencies := false,
    //    jsDependencies ++= clientJsDeps.value,
    scalaJSUseMainModuleInitializer := true,

    // Automatically generate index-dev.html which uses *-fastopt.js
    resourceGenerators in Compile += Def.task {
      val source = (resourceDirectory in Compile).value / "index.html"
      val target = (resourceManaged in Compile).value / "index-dev.html"
      println(s"Generating $target")

      //  val fullFileName = (artifactPath in (Compile, fullOptJS)).value.getName
      //  val fastFileName = (artifactPath in (Compile, fastOptJS)).value.getName

      val fullFileName = (artifactPath in (Compile, fullOptJS)).value.getName.replace("opt.js", "opt-bundle.js")
      val fastFileName = (artifactPath in (Compile, fastOptJS)).value.getName.replace("opt.js", "opt-bundle.js")

      IO.writeLines(target, IO.readLines(source).map { line =>
        line.replace(fullFileName, fastFileName)
      })

      Seq(target)
    }.taskValue
  )

