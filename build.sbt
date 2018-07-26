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

lazy val `web-client` = project.in(file("web/vbds-scala-js"))
  .enablePlugins(WorkbenchPlugin)
  .settings(
    libraryDependencies ++= Dependencies.webClient.value,
    skip in packageJSDependencies := false,
    jsDependencies ++= Dependencies.clientJsDeps.value,
    scalaJSUseMainModuleInitializer := true,
    // Automatically generate index-dev.html which uses *-fastopt.js
    resourceGenerators in Compile += Def.task {
      val source = (resourceDirectory in Compile).value / "index.html"
      val target = (resourceManaged in Compile).value / "index-dev.html"
      println(s"Generating $target")

      val fullFileName = (artifactPath in (Compile, fullOptJS)).value.getName
      val fastFileName = (artifactPath in (Compile, fastOptJS)).value.getName

      IO.writeLines(target, IO.readLines(source).map { line =>
        line.replace(fullFileName, fastFileName)
      })

      Seq(target)
    }.taskValue
  )
