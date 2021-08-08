lazy val `vbds-server` = project
  .enablePlugins(DeployApp, AutoMultiJvm)
  .settings(
    libraryDependencies ++= Dependencies.vbdsServer
  )
  .dependsOn(`vbds-client` % "test")

lazy val `vbds-client` = project
  .enablePlugins(DeployApp, VbdsBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.vbdsClient
  )
