import Dependencies._

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSBundlerPlugin, WorkbenchPlugin)
  .settings(
    inThisBuild(
      List(
        organization := "vbds",
        version := "0.1-SNAPSHOT",
        scalaVersion := "2.12.6"
      )),
    name := "vbds-scala-js",
    npmDependencies in Compile ++= Seq(
//      "jquery" -> "3.3.1",
//      "bootstrap" -> "4.1.1",
      "js9" -> "2.1.0"
    ),
    libraryDependencies ++= clientDeps.value,
    skip in packageJSDependencies := false,
//    jsDependencies ++= clientJsDeps.value,
    scalaJSUseMainModuleInitializer := true
  )

// Automatically generate index-dev.html which uses *-fastopt.js
resourceGenerators in Compile += Def.task {
  val source = (resourceDirectory in Compile).value / "index.html"
  val target = (resourceManaged in Compile).value / "index-dev.html"

//  val fullFileName = (artifactPath in (Compile, fullOptJS)).value.getName
//  val fastFileName = (artifactPath in (Compile, fastOptJS)).value.getName

  val fullFileName = (artifactPath in (Compile, fullOptJS)).value.getName.replace("opt.js", "opt-bundle.js")
  val fastFileName = (artifactPath in (Compile, fastOptJS)).value.getName.replace("opt.js", "opt-bundle.js")

  IO.writeLines(target, IO.readLines(source).map { line =>
    line.replace(fullFileName, fastFileName)
  })

  Seq(target)
}.taskValue
