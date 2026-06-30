import sbt._

object Libs {
  val ScalaVersion = "3.6.4"

  val `scalatest`       = "org.scalatest"    %% "scalatest"       % "3.2.19"  // Apache License 2.0 (aligned w/ csw-6.0.0)
  val `scopt`           = "com.github.scopt" %% "scopt"           % "4.1.0"   // MIT License (aligned w/ csw-6.0.0)
  val `logback-classic` = "ch.qos.logback"    % "logback-classic" % "1.5.17"  // EPL v1.0 / LGPL 2.1 (aligned w/ csw-6.0.0)
  val `boopickle`       = "io.suzaku"        %% "boopickle"       % "1.4.0"   // Apache License 2.0 (Scala 3-capable)
  val `commons-io`      = "commons-io"        % "commons-io"      % "2.11.0"  // Apache License 2.0

  // Removed in the csw-6.0.0 / Pekko / Scala 3 port:
  //   scala-async                 -- Scala 2-only, and was not wired into either module
  //   akka-management-cluster-http -- defined-but-unused
}

object CSW {
  private val Org     = "com.github.tmtsoftware.csw"
  private val Version = "6.0.0"

  val `csw-network-utils`   = Org %% "csw-network-utils"   % Version
  val `csw-location-client` = Org %% "csw-location-client" % Version
  val `csw-commons`         = Org %% "csw-commons"         % Version
}

object Pekko { // all pekko is Apache License 2.0
  val Version = "1.1.3"
  val Org     = "org.apache.pekko"

  val `pekko-stream`             = Org %% "pekko-stream"              % Version
  val `pekko-stream-typed`       = Org %% "pekko-stream-typed"        % Version
  val `pekko-stream-testkit`     = Org %% "pekko-stream-testkit"      % Version
  val `pekko-actor`              = Org %% "pekko-actor"               % Version
  val `pekko-actor-typed`        = Org %% "pekko-actor-typed"         % Version
  val `pekko-actor-testkit-typed`= Org %% "pekko-actor-testkit-typed" % Version
  val `pekko-distributed-data`   = Org %% "pekko-distributed-data"    % Version
  val `pekko-multi-node-testkit` = Org %% "pekko-multi-node-testkit"  % Version
  val `pekko-cluster`            = Org %% "pekko-cluster"             % Version
  val `pekko-cluster-tools`      = Org %% "pekko-cluster-tools"       % Version
  val `pekko-cluster-typed`      = Org %% "pekko-cluster-typed"       % Version
  val `pekko-slf4j`              = Org %% "pekko-slf4j"               % Version
}

object PekkoHttp { // Apache License 2.0
  val Version = "1.1.0"
  val Org     = "org.apache.pekko"

  val `pekko-http`            = Org %% "pekko-http"            % Version
  val `pekko-http-core`       = Org %% "pekko-http-core"       % Version
  val `pekko-http-testkit`    = Org %% "pekko-http-testkit"    % Version
  val `pekko-http-spray-json` = Org %% "pekko-http-spray-json" % Version
}