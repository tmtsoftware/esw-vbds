lazy val `vbds-server` = project
  .enablePlugins(DeployApp, VbdsBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.vbdsServer
  )

lazy val `vbds-client` = project
  .enablePlugins(DeployApp, VbdsBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.vbdsClient
  )
