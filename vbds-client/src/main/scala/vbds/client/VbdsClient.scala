package vbds.client

import java.io.File
import java.nio.file.Path

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{MergeHub, Source, SourceQueueWithComplete}
import vbds.client.VbdsClient.Subscription
import vbds.client.WebSocketActor.ReceivedFile

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}

object VbdsClient {
  trait Subscription {
    def unsubscribe(): Unit
    val httpResponse: Future[HttpResponse]
  }
}

/**
 * An akka-http based command line client for the vbds-server.
 *
 * @param name the name of this client, for logging
 * @param host the HTTP host where the vbds-server is running
 * @param port the HTTP port number to use to access the vbds-server
 * @param chunkSize optional chunk size for exchanging image data
 * @param system akka actor system
 * @param mat akka actor materializer
 */
class VbdsClient(name: String, host: String, port: Int, chunkSize: Int = 1024 * 1024)(implicit val system: ActorSystem,
                                                                                      implicit val mat: Materializer) {

  implicit val executionContext = system.dispatcher
  val adminRoute                = "/vbds/admin/streams"
  val accessRoute               = "/vbds/access/streams"
  val transferRoute             = "/vbds/transfer/streams"
  val maxFrameLengthBytes       = 1024 * 1024

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName

    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  val log = Logging(system, this)

  /**
   * Creates a stream with the given name
   */
  def createStream(streamName: String): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"http://$host:$port$adminRoute/$streamName"))
  }

  /**
   * List the current streams (XXX TODO: Unpack the HTTP response JSON and return a List[String])
   */
  def listStreams(): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(uri = s"http://$host:$port$adminRoute"))
  }

  /**
   * Deletes the stream with the given name
   */
  def deleteStream(streamName: String): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(method = HttpMethods.DELETE, uri = s"http://$host:$port$adminRoute/$streamName"))
  }

  /**
   * Publishes a file (or a directory full of files) to the given stream, with the given delay between each publish.
   *
   * @param streamName name of stream
   * @param file       file or directory full of files to publish
   * @param delay      optional delay
   * @return future indicating when done
   */
  def publish(streamName: String, file: File, delay: FiniteDuration = Duration.Zero): Future[Done] = {

    val handler: ((Try[HttpResponse], Path)) => Unit = {
      case (Success(response), path) =>
        if (response.status == StatusCodes.Accepted) log.debug(s"Result for file: $path was successful")
        else log.error(s"Publish of $path returned unexpected status code: ${response.status}")
        response.discardEntityBytes() // don't forget this
      case (Failure(ex), path) =>
        log.error(s"Uploading file $path failed with $ex")
    }

    val uri = s"http://$host:$port$transferRoute/$streamName"
    val paths = if (file.isDirectory) {
      // Sort files: Note: Added this to test with extracted video images so they are posted in the correct order
      file.listFiles().map(_.toPath).toList.sortWith {
        case (p1, p2) => comparePaths(p1, p2)
      }
    } else {
      List(file.toPath)
    }
//    println(s"Publishing ${paths.size} files with delay of $delay")
    val uploader = new FileUploader(chunkSize)
    uploader.uploadFiles(streamName, uri, paths, delay, handler)
  }

  val fileNamePattern = """\d+""".r

  private def comparePaths(p1: Path, p2: Path): Boolean = {
    val s1 = p1.getFileName.toString
    val s2 = p2.getFileName.toString
    val a  = fileNamePattern.findAllIn(s1).toList.headOption.getOrElse("")
    val b  = fileNamePattern.findAllIn(s2).toList.headOption.getOrElse("")
    if (a.nonEmpty && b.nonEmpty)
      a.toInt.compareTo(b.toInt) < 0
    else
      s1.compareTo(s2) < 0
  }

  /**
   * Subscribes to the given stream
   *
   * @param streamName the name of the stream we are subscribed to
   * @param dir the directory in which to save the files received (if saveFiles is true)
   * @param inQueue a queue to write messages to when a file is received
   * @param saveFiles if true, save the files in the given dir (Set to false for throughput tests)

   * @return the HTTP response
   */
  def subscribe(streamName: String,
                dir: File,
                inQueue: SourceQueueWithComplete[ReceivedFile],
                saveFiles: Boolean): Subscription = {
    log.debug(s"subscribe to $streamName")

    // We need a Source for writing to the websocket, but we want a Sink:
    // This provides a Sink that feeds the Source.
    val (outSink, outSource) = MergeHub.source[Message].preMaterialize()

//    // Queue to send to server to acknowledge message, for flow control
//    val outQueue = Source
//      .queue[String](1, OverflowStrategy.backpressure)
//      .map(TextMessage(_))
//      .map { x =>
//        println(s"\nXXXXXXXXXXX in outsource: $x\n")
//        x
//      }
//      .to(outSink)
//      .run()

    val receiver   = system.actorOf(WebSocketActor.props(name, streamName, dir, inQueue, outSink, saveFiles))
    val wsListener = new WebSocketListener
    val uri = Uri(s"ws://$host:$port$accessRoute/$streamName")
    val result = wsListener.subscribe(uri, receiver, outSource)

    // Send message to server to get started
//    outQueue.offer("ACK")

    result
  }

}
