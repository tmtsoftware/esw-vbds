lazy val `vbds-server` = project
  .enablePlugins(DeployApp)
  .settings(
    libraryDependencies ++= Dependencies.vbdsServer
  )
