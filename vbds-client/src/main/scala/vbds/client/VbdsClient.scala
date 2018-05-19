package vbds.client

import java.io.File
import java.nio.file.Path

import akka.Done
import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.SourceQueueWithComplete
import vbds.client.WebSocketActor.ReceivedFile

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}

/**
 * An akka-http based command line client for the vbds-server.
 *
 * @param host the HTTP host where the vbds-server is running
 * @param port the HTTP port number to use to access the vbds-server
 * @param chunkSize optional chunk size for exchanging image data
 * @param system akka actor system
 * @param mat akka actor materializer
 */
class VbdsClient(host: String, port: Int, chunkSize: Int = 1024 * 1024)(implicit val system: ActorSystem,
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
    // XXX TODO: Change to only accept single file and return HttpResponse?

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
      // Sort files (XXX FIXME: Not needed: Added this to test with extracted video images...)
      file.listFiles().map(_.toPath).toList.sortWith {
        case (p1, p2) => comparePaths(p1, p2)
      }
    } else {
      List(file.toPath)
    }
    val uploader = new FileUploader(chunkSize)
    uploader.uploadFiles(streamName, uri, paths, delay, handler)
  }

  // --- XXX FIXME: Not needed: Added this to test with extracted video images...) ---
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
  // ---

  /**
    * Subscribes to the given stream
    *
    * @param streamName the name of the stream we are subscribed to
    * @param dir the directory in which to save the files received (if saveFiles is true)
    * @param queue a queue to write messages to when a file is received
    * @param saveFiles if true, save the files in the given dir (Set to false for throughput tests)
    * @param delay optional delay to simulate a slow subscriber

    * @return the HTTP response
    */
  def subscribe(streamName: String,
                dir: File,
                queue: SourceQueueWithComplete[ReceivedFile],
                saveFiles: Boolean,
                delay: FiniteDuration = Duration.Zero): Future[HttpResponse] = {
    log.debug(s"subscribe to $streamName")
    val receiver   = system.actorOf(WebSocketActor.props(streamName, dir, queue, saveFiles, delay))
    val wsListener = new WebSocketListener
    wsListener.subscribe(Uri(s"ws://$host:$port$accessRoute/$streamName"), receiver)
  }

}
