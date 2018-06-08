package vbds.server.app

import java.net.InetAddress

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Success}

/**
  * VIZ Bulk Data System HTTP server and Akka cluster.
  * This is the command line app used to start the server.
  */
object VbdsServerApp extends App {
  val systemName = "vbds-system"

  // Command line options
  private case class Options(name: String = "vbds",
                             httpHost: String = "127.0.0.1",
                             httpBindHost: Option[String] = None,
                             httpPort: Int = 0,
                             akkaHost: String = "127.0.0.1",
                             akkaBindHost: Option[String] = None,
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

    opt[String]("http-bind-host") valueName "<hostname>" action { (x, c) =>
      c.copy(httpBindHost = Some(x))
    } text "The HTTP server host name to bind to (default: same as --http-host)"

    opt[Int]("http-port") valueName "<number>" action { (x, c) =>
      c.copy(httpPort = x)
    } text "The HTTP server port number (default: 0)"

    opt[String]("akka-host") valueName "<hostname>" action { (x, c) =>
      c.copy(akkaHost = x)
    } text "The Akka system host name (default: 127.0.0.1)"

    opt[String]("akka-bind-host") valueName "<hostname>" action { (x, c) =>
      c.copy(akkaBindHost = Some(x))
    } text "The Akka system host name to bind to (default: same as --akka-host)"

    opt[Int]("akka-port") valueName "<number>" action { (x, c) =>
      c.copy(akkaPort = x)
    } text "The Akka system port number (default: 0)"

    opt[String]('s', "seeds") valueName "<host>:<port>,<host>:<port>,..." action {
      (x, c) =>
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

  // Gets the akka seed nodes config line
  private def getSeedNodes(options: Options): String = {
    if (options.clusterSeeds.nonEmpty) {
      val seeds = options.clusterSeeds
        .split(",")
        .map(s => s""""akka.tcp://$systemName@$s"""")
        .mkString(",")
      s"akka.cluster.seed-nodes=[$seeds]"
    } else ""
  }

  // Run the application
  private def run(options: Options): Unit = {
    val seedNodes = getSeedNodes(options)
    if (seedNodes.isEmpty) {
      println(
        "Please specify one or more seed nodes via the -s (or --seeds) option.")
      System.exit(1)
    }

    // Generate the akka config for the akka and http ports as well as the cluster seed nodes
    val s = s"""
               akka.remote.netty.tcp.hostname=${options.akkaHost}
               akka.remote.netty.tcp.bind-hostname=${options.akkaBindHost.getOrElse(options.akkaHost)}
               akka.remote.netty.tcp.port=${options.akkaPort}
               akka.remote.artery.canonical.hostname=${options.akkaHost}
               akka.remote.artery.canonical.bind-hostname=${options.akkaBindHost.getOrElse(options.akkaHost)}
               akka.remote.artery.canonical.port=${options.akkaPort}
               $seedNodes
            """
    val config = ConfigFactory.parseString(s).withFallback(ConfigFactory.load())

    println(s"\nXXXXXXXXX\n${options.name} akka hostname=${options.akkaHost}=${config.getString("akka.remote.netty.tcp.hostname")}, akka bind-host=${options.akkaBindHost}=${config.getString("akka.remote.netty.tcp.bind-hostname")}\n")

    implicit val system = ActorSystem(systemName, config)
    import system.dispatcher
    println(s"YYYYY ${options.name}: system = $system")

    VbdsServer.start(options.httpHost, options.httpPort).onComplete {
        case Success(result) =>
          println(s"HTTP Server running on: http:/${result.localAddress}")
        case Failure(error) =>
          println(error)
          System.exit(1)
      }
  }
}
