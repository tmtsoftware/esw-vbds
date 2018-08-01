package vbds.client.app

import java.io.File

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import vbds.client.VbdsClient

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import vbds.client.WebSocketActor._

/**
 * A VIZ Bulk Data System HTTP client command line application.
 */
object VbdsClientApp extends App {
  implicit val system       = ActorSystem("vbdsClient")
  implicit val materializer = ActorMaterializer()

  // Command line options
  private case class Options(name: String = "vbds",
                             host: String = "127.0.0.1",
                             port: Int = 80,
                             create: Option[String] = None,
                             contentType: Option[String] = None,
                             delete: Option[String] = None,
                             subscribe: Option[String] = None,
                             dir: Option[String] = None,
                             action: Option[String] = None,
                             list: Boolean = false,
                             publish: Option[String] = None,
                             delay: Option[String] = None,
                             data: Option[File] = None,
                             chunkSize: Int = 1024 * 1024)

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("vbds-client") {
    head("vbds-client", "0.0.1") // XXX FIXME: BuildInfo is in vbds-server, but don't want to depend on it (also issue with application.conf)

    opt[String]('n', "name") valueName "<name>" action { (x, c) =>
      c.copy(name = x)
    } text "The name of the vbds-server server(default: vbds)" // XXX TODO: Implement location service lookup via name

    opt[String]("host") valueName "<host name>" action { (x, c) =>
      c.copy(host = x)
    } text "The VBDS HTTP server host name (default: 127.0.0.1)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text "The VBDS HTTP server port number (default: 80)"

    opt[String]("create") valueName "<stream name>" action { (x, c) =>
      c.copy(create = Some(x))
    } text "Creates a new VBDS stream with the given name"

    opt[String]("contentType") valueName "<content-type>" action { (x, c) =>
      c.copy(contentType = Some(x))
    } text "Specifies the content type of the files in the stream"

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
    } text "Directory to hold received data files (default: current directory)"

    opt[String]('a', "action") valueName "<shell-command>" action { (x, c) =>
      c.copy(action = Some(x))
    } text "A shell command to execute when a new file is received (args: file-name)"

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

  // Run the application (The actor system is only used locally, no need for remote)
  private def run(options: Options): Unit = {

    val client = new VbdsClient(options.name, options.host, options.port)
    options.create.foreach(s => handleHttpResponse(s"create $s", client.createStream(s, options.contentType.getOrElse(""))))
    options.delete.foreach(s => handleHttpResponse(s"delete $s", client.deleteStream(s)))
    if (options.list) handleHttpResponse("list", client.listStreams())

    val delay = options.delay.map(Duration(_).asInstanceOf[FiniteDuration]).getOrElse(Duration.Zero)
    if (options.publish.isDefined && options.data.isDefined) {
      options.publish.foreach(s => handlePublishResponse(s"publish $s", client.publish(s, options.data.get, delay)))
    }

    val clientActor = system.actorOf(ClientActor.props(options))

    options.subscribe.foreach(
      s => client.subscribe(s, new File(options.dir.getOrElse(".")), clientActor, saveFiles = true)
    )
  }

  // Actor to receive and acknowledge files
  private object ClientActor {
    def props(options: Options): Props = Props(new ClientActor(options))

//    // XXX Temp
//    def checkFits(r: ReceivedFile): Future[Done] = {
//      FileIO
//        .fromPath(r.path)
//        .via(
//          Framing
//            .delimiter(ByteString("\n"), 80, allowTruncation = true)
//            .map(_.utf8String)
//        )
//        .take(1)
//        .runForeach {s =>
//          if (!s.startsWith("SIMPLE")) println(s"XXX $r.path is not a FITS file")
//        }
//    }
  }

  private class ClientActor(options: Options) extends Actor with ActorLogging {

    def receive: Receive = {
      case r: ReceivedFile =>
        println(s"Received ${r.count} files for stream ${r.streamName}")
        options.action.foreach(doAction(r, _))

//        // XXX temp
//        Await.ready(checkFits(r), 5.seconds)

        r.path.toFile.delete()
        sender() ! r
    }
  }

  // XXX TODO: Change to actor or callback
  private def doAction(r: ReceivedFile, action: String): Unit = {
    import sys.process._
    try {
      s"$action ${r.path}".!
    } catch {
      case ex: Exception => println(s"Error: Action for file ${r.count} of stream ${r.streamName} failed: $ex")
    }
  }

  // Prints the result of the HTTP request and exits
  private def handleHttpResponse(command: String, resp: Future[HttpResponse])(implicit system: ActorSystem): Unit = {
    import system.dispatcher
    resp.onComplete {
      case Success(_) =>
        system.terminate().onComplete {
          case Success(_) =>
            System.exit(0)
          case Failure(_) =>
//            ex.printStackTrace()
            System.exit(0)
        }
      case Failure(ex) =>
        println(s"$command failed: $ex")
        ex.printStackTrace()
    }
  }

  private def handlePublishResponse(command: String, resp: Future[Done])(implicit system: ActorSystem): Unit = {
    import system.dispatcher
    resp.onComplete {
      case Success(_) =>
        system.terminate().onComplete {
          case Success(_) =>
            System.exit(0)
          case Failure(_) =>
            System.exit(0)
        }
      case Failure(ex) =>
        println(s"$command failed: $ex")
        ex.printStackTrace()
    }
  }
}
