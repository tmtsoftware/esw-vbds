package vbds.server.app

import csw.network.utils.{Networks, SocketUtils}

/**
 * VIZ Bulk Data System HTTP server and Akka cluster.
 * This is the command line app used to start the server.
 */
object VbdsServerApp {

  // Command line options
  private case class Options(
      name: String = "vbds",
      httpPort: Int = 0,
      pekkoPort: Int = 0,
      clusterSeeds: String = ""
  )

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("vbds-server") {
    head(BuildInfo.name, BuildInfo.version)

    opt[String]('n', "name") valueName "<name>" action { (x, c) =>
      c.copy(name = x)
    } text "The name of this server (For the Location Service: default: vbds)"

    opt[Int]("http-port") valueName "<number>" action { (x, c) =>
      c.copy(httpPort = x)
    } text "The HTTP server port number (default: 0 for random port)"

    opt[Int]("pekko-port") valueName "<number>" action { (x, c) =>
      c.copy(pekkoPort = x)
    } text "The Pekko system port number (default: 0 for random port)"

    opt[String]('s', "seeds") valueName "<host>:<port>,<host>:<port>,..." action { (x, c) =>
      c.copy(clusterSeeds = x)
    } text "Optional list of cluster seeds in the form 'host:port,host:port,...' for joining the VBDS cluster"

    help("help")
    version("version")
  }

  // Parse the command line options
  def main(args: Array[String]): Unit = {
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
  }

  // Run the application
  private def run(options: Options): Unit = {
    val host = Networks.publicInterface(None).hostname
    val pekkoPort = if (options.pekkoPort != 0) options.pekkoPort else SocketUtils.getFreePort
    val httpPort = if (options.httpPort != 0) options.httpPort else SocketUtils.getFreePort
    val clusterSeeds = if (options.clusterSeeds.nonEmpty)
      options.clusterSeeds else s"${host}:$pekkoPort"

    VbdsServer.start(host, httpPort, pekkoPort, options.name, clusterSeeds)
  }
}
