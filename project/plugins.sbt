//addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
//addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.4.0")
//addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "1.9.3")
addSbtPlugin("com.github.sbt"     % "sbt-native-packager"      % "1.9.16")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"            % "0.10.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-multi-jvm"            % "0.4.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")

// web client
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.8.0")

// Requires local plugin build and publishLocal, since existing plugin was abandoned
//addSbtPlugin("com.lihaoyi"      % "workbench"          % "0.4.2")

addSbtPlugin("org.scala-js"     % "sbt-jsdependencies" % "1.0.2")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"        % "0.6.1")
