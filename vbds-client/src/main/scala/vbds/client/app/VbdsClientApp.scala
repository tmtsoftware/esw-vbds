package vbds.client.app

import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import vbds.client.VbdsClient

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import vbds.client.WebSocketActor._

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
                             chunkSize: Int = 1024 * 1024)

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
    } text "The VBDS HTTP server port number (default: 80)"

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
    val config = ConfigFactory.parseString(
      s"""
         | akka.remote.netty.tcp.hostname=127.0.0.1
         | akka.remote.netty.tcp.port=0
         | akka.remote.netty.tcp.bind-hostname=127.0.0.1
         | akka.remote.artery.canonical.hostname=127.0.0.1
         | akka.remote.artery.canonical.port=0
         | akka.remote.artery.canonical.bind-hostname=127.0.0.1
            """).withFallback(ConfigFactory.load())
    println(s"XXXX ${options.name}: host = ${config.getString("akka.remote.netty.tcp.hostname")}")

    implicit val system = ActorSystem(options.name, config)
    println(s"XXXX ${options.name}: bind-host = ${config.getString("akka.remote.netty.tcp.bind-hostname")}")

    implicit val materializer = ActorMaterializer()

    val client = new VbdsClient(options.name, options.host, options.port)
    options.create.foreach(s => handleHttpResponse(s"create $s", client.createStream(s)))
    options.delete.foreach(s => handleHttpResponse(s"delete $s", client.deleteStream(s)))
    if (options.list) handleHttpResponse("list", client.listStreams())

    val delay = options.delay.map(Duration(_).asInstanceOf[FiniteDuration]).getOrElse(Duration.Zero)
    if (options.publish.isDefined && options.data.isDefined) {
      options.publish.foreach(s => handlePublishResponse(s"publish $s", client.publish(s, options.data.get, delay)))
    }

    val queue = Source
      .queue[ReceivedFile](1, OverflowStrategy.dropHead)
      .map { r =>
        println(s"Received file ${r.count} for stream ${r.streamName}")
        options.action.foreach(doAction(r, _))
        r.path.toFile.delete()
      }
      .to(Sink.ignore)
      .run()

    options.subscribe.foreach(
      s => client.subscribe(s, new File(options.dir.getOrElse(".")), queue, saveFiles = true)
    )
  }

  // XXX TODO: Change to actor or callback
  private def doAction(r: ReceivedFile, action: String): Unit = {
    import sys.process._
    try {
      val x = s"$action ${r.path}".!
    } catch {
      case ex: Exception => println(s"Error: Action for file ${r.count} of stream ${r.streamName} failed: $ex")
    }
  }

  // Prints the result of the HTTP request and exits
  private def handleHttpResponse(command: String, resp: Future[HttpResponse])(implicit system: ActorSystem): Unit = {
    import system.dispatcher
    resp.onComplete {
      case Success(_) =>
      case Failure(ex) =>
        println(s"$command failed: $ex")
        ex.printStackTrace()
    }
  }

  private def handlePublishResponse(command: String, resp: Future[Done])(implicit system: ActorSystem): Unit = {
    import system.dispatcher
    resp.onComplete {
      case Success(_) =>
      case Failure(ex) =>
        println(s"$command failed: $ex")
        ex.printStackTrace()
    }
  }
}
