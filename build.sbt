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
  val clientJsDeps = Def.setting(Seq(
    "org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js"
  ))

lazy val `web-client` = project.in(file("web/vbds-scala-js"))
  .enablePlugins(WorkbenchPlugin)
  .settings(
    libraryDependencies ++= Dependencies.webClient.value,
    skip in packageJSDependencies := false,
    jsDependencies ++= clientJsDeps.value,
    scalaJSUseMainModuleInitializer := true,
      // Automatically generate index-dev.html which uses *-fastopt.js
    resourceGenerators in Compile += Def.task {
      val source = (resourceDirectory in Compile).value / "index.html"
      val target = (resourceManaged in Compile).value / "index-dev.html"
      println(s"Generating $target")

      val source2 = (resourceDirectory in Compile).value / "index-with-canvas.html"
      val target2 = (resourceManaged in Compile).value / "index-with-canvas-dev.html"
      println(s"Generating $target2")

      val fullFileName = (artifactPath in (Compile, fullOptJS)).value.getName
      val fastFileName = (artifactPath in (Compile, fastOptJS)).value.getName

      IO.writeLines(target, IO.readLines(source).map { line =>
        line.replace(fullFileName, fastFileName)
      })

      IO.writeLines(target2, IO.readLines(source2).map { line =>
        line.replace(fullFileName, fastFileName)
      })

      Seq(target, target2)
    }.taskValue
  )
