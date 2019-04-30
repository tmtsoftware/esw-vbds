package vbds.server.app

/**
 * VIZ Bulk Data System HTTP server and Akka cluster.
 * This is the command line app used to start the server.
 */
object VbdsServerApp extends App {

  // Command line options
  private case class Options(name: String = "vbds",
                             httpHost: String = "127.0.0.1",
                             httpPort: Int = 0,
                             akkaHost: String = "127.0.0.1",
                             akkaPort: Int = 0,
                             clusterSeeds: String = "")

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("vbds-server") {
    head(BuildInfo.name, BuildInfo.version)

    opt[String]('n', "name") valueName "<name>" action { (x, c) =>
      c.copy(name = x)
    } text "The name of this server(default: vbds)"

    opt[String]("http-host") valueName "<hostname>" action { (x, c) =>
      c.copy(httpHost = x)
    } text "The HTTP server host name (default: 127.0.0.1)"

    opt[Int]("http-port") valueName "<number>" action { (x, c) =>
      c.copy(httpPort = x)
    } text "The HTTP server port number (default: 0)"

    opt[String]("akka-host") valueName "<hostname>" action { (x, c) =>
      c.copy(akkaHost = x)
    } text "The Akka system host name (default: 127.0.0.1)"

    opt[Int]("akka-port") valueName "<number>" action { (x, c) =>
      c.copy(akkaPort = x)
    } text "The Akka system port number (default: 0)"

    opt[String]('s', "seeds") valueName "<host>:<port>,<host>:<port>,..." action { (x, c) =>
      c.copy(clusterSeeds = x)
    } text "Optional list of cluster seeds in the form host:port,host:port,..."

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  // Run the application
  private def run(options: Options): Unit = {
    import options._

    if (clusterSeeds.isEmpty) {
      println("Please specify one or more seed nodes via the -s (or --seeds) option.")
      System.exit(1)
    }

    VbdsServer.start(httpHost, httpPort, akkaHost, akkaPort, clusterSeeds)
  }
}
