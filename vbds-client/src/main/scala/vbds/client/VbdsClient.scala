package vbds.client

import java.io.File
import java.nio.file.Path

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.event.{LogSource, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.MergeHub
import vbds.client.VbdsClient.Subscription

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
  def createStream(streamName: String, contentType: String): Future[HttpResponse] = {
    if (contentType.nonEmpty)
      Http().singleRequest(
        HttpRequest(method = HttpMethods.POST, uri = s"http://$host:$port$adminRoute/$streamName?contentType=$contentType")
      )
    else
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
   * @param suffix     optional suffix for files in directory to publish
   * @param delay      optional delay
   * @param stats      if true print timing statistics
   * @return future indicating when done
   */
  def publish(streamName: String, file: File, suffix: Option[String], delay: FiniteDuration = Duration.Zero, stats: Boolean = false): Future[Done] = {

    val startTime: Long = System.currentTimeMillis()
    val printInterval   = 100 // TODO: Make this an option
    var count           = 0

    // Print timing statistics
    def logStats(path: Path): Unit = {
      count = count + 1
      if (count % printInterval == 0) {
        val testSecs          = (System.currentTimeMillis() - startTime) / 1000.0
        val secsPerFile       = testSecs / count
        val testFileSizeBytes = path.toFile.length()
        val fileSizeMb        = testFileSizeBytes / 1000000.0
        val mbPerSec          = (fileSizeMb * count) / testSecs
        val hz                = 1.0 / secsPerFile
        log.info(
          f"$name: $count: Published $count $testFileSizeBytes byte files in $testSecs seconds ($secsPerFile%1.4f secs per file, $hz%1.4f hz, $mbPerSec%1.4f mb/sec)"
        )
      }
    }

    val handler: ((Try[HttpResponse], Path)) => Unit = {
      case (Success(response), path) =>
        if (response.status == StatusCodes.Accepted) {
          if (stats) logStats(path) else log.info(s"Result for file: $path was successful")
        } else {
          log.error(s"Publish of $path returned unexpected status code: ${response.status}")
          response.discardEntityBytes() // don't forget this
        }
      case (Failure(ex), path) =>
        log.error(s"Uploading file $path failed with $ex")
    }

    val uri = s"http://$host:$port$transferRoute/$streamName"
    val paths = if (file.isDirectory) {
      val filter = suffix.map(s => (f: File) => f.getName.endsWith(s)).getOrElse((f: File) => true)
      // Sort files: Note: Added this to test with extracted video images so they are posted in the correct order
      file.listFiles().filter(filter).map(_.toPath).toList.sortWith {
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
   * @param clientActor an actor to write messages to when a file is received (using 'ask', actor should reply to each message)
   * @param saveFiles if true, save the files in the given dir (Set to false for throughput tests)

   * @return the HTTP response
   */
  def subscribe(streamName: String, dir: File, clientActor: ActorRef, saveFiles: Boolean): Subscription = {
    log.debug(s"subscribe to $streamName")

    // We need a Source for writing to the websocket, but we want a Sink:
    // This provides a Sink that feeds the Source.
    val (outSink, outSource) = MergeHub.source[Message](1).preMaterialize()
    val receiver             = system.actorOf(WebSocketActor.props(name, streamName, dir, clientActor, outSink, saveFiles))
    val wsListener           = new WebSocketListener
    val uri                  = Uri(s"ws://$host:$port$accessRoute/$streamName")
    wsListener.subscribe(uri, receiver, outSource)
  }

}
