package vbds.client.app

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import vbds.client.VbdsClient

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

/**
  * A VIZ Bulk Data System HTTP client command line application.
  */
object VbdsClientApp extends App {

  // Command line options
  private case class Options(name: String = "vbds",
                             host: String = "127.0.0.1",
                             port: Int = 80,
                             create: Option[String] = None,
                             delete: Option[String] = None,
                             subscribe: Option[String] = None,
                             dir: Option[String] = None,
                             action: Option[String] = None,
                             list: Boolean = false,
                             publish: Option[String] = None,
                             delay: Option[String] = None,
                             data: Option[File] = None,
                             chunkSize: Int = 1024*1024)

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("vbds-client") {
    head("vbds-client", "0.0.1") // XXX FIXME: BuildInfo is in vbds-server, but don't want to depend on it (also issue with application.conf)

    opt[String]('n', "name") valueName "<name>" action { (x, c) =>
      c.copy(name = x)
    } text "The name of the vbds-server server(default: vbds)" // XXX TODO: Implement location service lookup via name

    opt[String]('h', "host") valueName "<host name>" action { (x, c) =>
      c.copy(host = x)
    } text "The VBDS HTTP server host name (default: 127.0.0.1)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text "The VDBS HTTP server port number (default: 80)"

    opt[String]("create") valueName "<stream name>" action { (x, c) =>
      c.copy(create = Some(x))
    } text "Creates a new VBDS stream with the given name"

    opt[String]("delete") valueName "<stream name>" action { (x, c) =>
      c.copy(delete = Some(x))
    } text "Deletes the VBDS stream with the given name"

    opt[Unit]('l', "list") action { (_, c) =>
      c.copy(list = true)
    } text "List the available streams"

    opt[String]("subscribe") valueName "<stream name>" action { (x, c) =>
      c.copy(subscribe = Some(x))
    } text "Subscribes to the given VBDS stream (see --action option)"

    opt[String]("dir") valueName "<path>" action { (x, c) =>
      c.copy(dir = Some(x))
    } text "Directory to hold received image files (default: current directory)"

    opt[String]('a', "action") valueName "<shell-command>" action { (x, c) =>
      c.copy(action = Some(x))
    } text "A shell command to execute when a new file is received (args: stream-name file-name)"

    opt[String]("publish") valueName "<stream-name>" action { (x, c) =>
      c.copy(publish = Some(x))
    } text "Publish to the given stream (see --data option)"

    opt[String]("delay") valueName "<duration>" action { (x, c) =>
      c.copy(delay = Some(x))
    } text "Delay between publishing files in a directory (see --data)"

    opt[File]("data") valueName "<file-name>" action { (x, c) =>
      c.copy(data = Some(x))
    } text "Specifies the file (or directory full of files) to publish"

    opt[Int]("chunk-size") valueName "<num-bytes>" action { (x, c) =>
      c.copy(chunkSize = x)
    } text "Optional chunk size (to tune file transfer performance)"

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
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val client = new VbdsClient(options.host, options.port)
    options.create.foreach(s => handleHttpResponse(client.createStream(s)))
    options.delete.foreach(s => handleHttpResponse(client.deleteStream(s)))
    if (options.list) handleHttpResponse(client.listStreams())

    if (options.publish.isDefined && options.data.isDefined) {
      val delay = options.delay.map(Duration(_)).getOrElse(Duration.Zero)
      options.publish.foreach(s => handleHttpResponse(client.publish(s, options.data.get, delay.asInstanceOf[FiniteDuration])))
    }

    options.subscribe.foreach(s => client.subscribe(s, options.dir.getOrElse("."), options.action))
  }

  // Prints the result of the HTTP request and exits
  private def handleHttpResponse(resp: Future[Any]): Unit = {
    val result = Try(Await.result(resp, 60.seconds))
    result match {
      case Success(res) =>
        println(res)
      case Failure(ex) =>
        ex.printStackTrace()
    }
//    Await.ready(system.terminate(), 60.seconds)
  }
}
