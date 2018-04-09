package vbds.server.app

import akka.actor.ActorSystem
import vbds.server.VbdsServer

import scala.util.{Failure, Success}

object VbdsServerApp extends App {

  // Command line options
  private case class Options(name: String = "vbds",
                             host: String = "localhost",
                             port: Int = 9999)

  implicit val system = ActorSystem("vbds-system")

  import system.dispatcher

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("vbds") {
    head("vbds", System.getProperty("VERSION"))

    opt[String]('n', "name") valueName "<name>" action { (x, c) =>
      c.copy(name = x)
    } text "The name of this server(default: vbds)"

    opt[String]('h', "host") valueName "<hostname>" action { (x, c) =>
      c.copy(host = x)
    } text "The host name for this server (default: localhost)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text "The port number to use for the server (default: 9999)"

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
    new VbdsServer().start(options.host, options.port).onComplete {
      case Success(result) => println(result.localAddress)
      case Failure(error)  => println(error)
    }
  }
}
