package vbds.client.app

import java.io.File
import akka.Done
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.HttpResponse
import csw.location.api.models.{ComponentId, ComponentType, HttpLocation}
import csw.location.api.models.Connection.HttpConnection
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.prefix.models.{Prefix, Subsystem}
import vbds.client.VbdsClient
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import vbds.client.WebSocketActor.*
import vbds.server.app.BuildInfo

/**
 * A VIZ Bulk Data System HTTP client command line application.
 */
object VbdsClientApp extends App {
  implicit val system       = ActorSystem("vbdsClient")

  // Command line options
  private case class Options(name: String = "vbds",
                             create: Option[String] = None,
                             contentType: Option[String] = None,
                             delete: Option[String] = None,
                             subscribe: Option[String] = None,
                             dir: Option[String] = None,
                             action: Option[String] = None,
                             list: Boolean = false,
                             stats: Boolean = false,
                             repeat: Boolean = false,
                             saveFiles: Boolean = false,
                             statsInterval: Int = 1,
                             publish: Option[String] = None,
                             delay: Option[String] = None,
                             data: Option[File] = None,
                             suffix: Option[String] = None,
                             chunkSize: Int = 1024 * 1024)

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("vbds-client") {
    head(BuildInfo.name, BuildInfo.version)

    opt[String]('n', "name") valueName "<name>" action { (x, c) =>
      c.copy(name = x)
    } text "The name of the vbds-server server(default: vbds)"

    opt[String]("create") valueName "<stream name>" action { (x, c) =>
      c.copy(create = Some(x))
    } text "Creates a new VBDS stream with the given name"

    opt[String]("content-type") valueName "<content-type>" action { (x, c) =>
      c.copy(contentType = Some(x))
    } text "Specifies the content type of the files in the stream"

    opt[String]("delete") valueName "<stream name>" action { (x, c) =>
      c.copy(delete = Some(x))
    } text "Deletes the VBDS stream with the given name"

    opt[Unit]('l', "list") action { (_, c) =>
      c.copy(list = true)
    } text "List the available streams"

    opt[Unit]("stats") action { (_, c) =>
      c.copy(stats = true)
    } text "Print timing statistics when publishing files"

    opt[Unit]("repeat") action { (_, c) =>
      c.copy(repeat = true)
    } text "Keep publishing the same files forever, until killed (for testing)"

    opt[Unit]("save-files") action { (_, c) =>
      c.copy(saveFiles = true)
    } text "If true, save the files received by the subscription to the current directory with names like <streamName>-<count>"

    opt[Int]("stats-interval") action { (x, c) =>
      c.copy(statsInterval = x)
    } text "If --stats option was given, controls how often statistics are printed (default: 1 = every time)"

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

    opt[String]("suffix") valueName "<suffix>" action { (x, c) =>
      c.copy(suffix = Some(x))
    } text "Optional suffix for files to publish if the file given by --data is a directory"

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
    val locationService = HttpLocationServiceFactory.makeLocalClient(system.toTyped)
    val maybeLocation = Await.result(locationService.find(
      HttpConnection(
        ComponentId(
          Prefix(Subsystem.ESW, options.name),
          ComponentType.Service
        ))), 10.seconds)

    maybeLocation match {
      case Some(loc) =>
        runClient(options, loc)
      case None =>
        println(s"Could not locate VBDS server ${options.name}")
        System.exit(1)
    }
  }

  private def runClient(options: Options, loc: HttpLocation): Unit = {
    val client = new VbdsClient(options.name, loc.uri.getHost, loc.uri.getPort, options.chunkSize)
    options.create.foreach(s => handleHttpResponse(s"create $s", client.createStream(s, options.contentType.getOrElse(""))))
    options.delete.foreach(s => handleHttpResponse(s"delete $s", client.deleteStream(s)))
    if (options.list) handleHttpResponse("list", client.listStreams())

    val delay = options.delay.map(Duration(_).asInstanceOf[FiniteDuration]).getOrElse(Duration.Zero)
    if (options.publish.isDefined && options.data.isDefined) {
      options.publish.foreach(
        s =>
          handlePublishResponse(s"publish $s",
                                client.publish(s, options.data.get, options.suffix, delay, options.stats, options.statsInterval, options.repeat))
      )
    }

    val clientActor = system.actorOf(ClientActor.props(options))

    options.subscribe.foreach(
      s => client.subscribe(s, new File(options.dir.getOrElse(".")), clientActor, saveFiles = options.saveFiles)
    )
  }

  // Actor to receive and acknowledge files
  private object ClientActor {
    def props(options: Options): Props = Props(new ClientActor(options))
  }

  private class ClientActor(options: Options) extends Actor with ActorLogging {

    def receive: Receive = {
      case r: ReceivedFile =>
        println(s"Received ${r.count} files for stream ${r.streamName}")
        options.action.foreach(doAction(r, _))
        r.path.toFile.delete()
        sender() ! r
    }
  }

  // XXX TODO: Change to actor or callback
  private def doAction(r: ReceivedFile, action: String): Unit = {
    import sys.process.*
    try {
      s"$action ${r.path}".!
    } catch {
      case ex: Exception => println(s"Error: Action for file ${r.count} of stream ${r.streamName} failed: $ex")
    }
  }

  // Prints the result of the HTTP request and exits
  private def handleHttpResponse(command: String, resp: Future[HttpResponse])(implicit system: ActorSystem): Unit = {
    import system.dispatcher
    val timeout = 3.seconds
    try {
      val r = Await.result(resp, timeout)
      val s = Await.result(r.entity.toStrict(1.second).map(_.data.utf8String), timeout)
      println(s"${r.status}: $s")
      System.exit(0)
    } catch {
      case ex: Exception =>
        println(s"$command failed: $ex")
        System.exit(1)
    }
  }

  private def handlePublishResponse(command: String, resp: Future[Done]): Unit = {
    val timeout = 10.hours // might be publishing lots of files...
    try {
      Await.ready(resp, timeout)
      System.exit(0)
    } catch {
      case ex: Exception =>
        println(s"$command failed: $ex")
        System.exit(1)
    }
  }
}
