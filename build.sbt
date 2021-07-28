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
  .enablePlugins(JSDependenciesPlugin)
  .settings(
    libraryDependencies ++= Dependencies.webClient.value,
    packageJSDependencies / skip   := false,
    jsDependencies ++= clientJsDeps.value,
    scalaJSUseMainModuleInitializer := true,
    // Automatically generate index-dev.html which uses *-fastopt.js
    Compile / resourceGenerators  += Def.task {
      val source = (Compile / resourceDirectory).value / "index.html"
      val target = (Compile / resourceManaged).value / "index-dev.html"
      println(s"Generating $target")

      val source2 = (Compile / resourceDirectory).value / "index-with-canvas.html"
      val target2 = (Compile / resourceManaged).value / "index-with-canvas-dev.html"
      println(s"Generating $target2")

      val fullFileName = (Compile / fullOptJS / artifactPath).value.getName
      val fastFileName = (Compile / fastOptJS / artifactPath).value.getName

      IO.writeLines(target, IO.readLines(source).map { line =>
        line.replace(fullFileName, fastFileName)
      })

      IO.writeLines(target2, IO.readLines(source2).map { line =>
        line.replace(fullFileName, fastFileName)
      })

      Seq(target, target2)
    }.taskValue
  )
