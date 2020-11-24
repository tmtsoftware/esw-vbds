package vbds.client

import java.io.File
import java.nio.file.Path
import java.time.Instant

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.event.{LogSource, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.MergeHub
import vbds.client.VbdsClient.Subscription

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
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
 * @param chunkSize optional chunk size in bytes for exchanging image data
 * @param system akka actor system
 */
class VbdsClient(name: String, host: String, port: Int, chunkSize: Int = 1024 * 1024)(implicit val system: ActorSystem) {

  implicit val executionContext = system.dispatcher
  val adminRoute                = "/vbds/admin/streams"
  val accessRoute               = "/vbds/access/streams"
  val transferRoute             = "/vbds/transfer/streams"
//  val maxFrameLengthBytes       = 1024 * 1024

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
   * @param printInterval if stats is true, only print at this interval
   * @param repeat if true, keep streaming the same file or files until the process is killed (for testing)
   * @return future indicating when done
   */
  def publish(streamName: String,
              file: File,
              suffix: Option[String] = None,
              delay: FiniteDuration = Duration.Zero,
              stats: Boolean = false,
              printInterval: Int = 1,
              repeat: Boolean = false): Future[Done] = {

    val startTime: Long = System.currentTimeMillis()
    var count           = -10 // warmup for the first 10 files...
    var minLatency      = 1000.0
    var maxLatency      = 0.0
    var totalLatency    = 0.0

    // Print timing statistics. 'start' is the start time of the last publish
    def logStats(path: Path, start: Instant): Unit = {
      count = count + 1
      if (count > 0) {
        val latency = java.time.Duration.between(start, Instant.now).toNanos / 1000000000.0
        totalLatency = totalLatency + latency

        if (count % printInterval == 0) {
          val avgLatency = totalLatency / count
          minLatency = math.min(latency, minLatency)
          maxLatency = math.max(latency, maxLatency)
          val testSecs          = (System.currentTimeMillis() - startTime) / 1000.0
          val secsPerFile       = testSecs / count
          val testFileSizeBytes = path.toFile.length()
          val fileSizeMb        = testFileSizeBytes / 1000000.0
          val mbPerSec          = (fileSizeMb * count) / testSecs
          val hz                = 1.0 / secsPerFile
          log.info(
            f"$name: $count: Published $count $testFileSizeBytes byte files in $testSecs seconds ($secsPerFile%1.4f secs per file, $hz%1.4f hz, $mbPerSec%1.4f mb/sec, latency: $latency%1.4f sec (min: $minLatency%1.4f, max: $maxLatency%1.4f, avg: $avgLatency%1.4f))"
          )
        }
      }
    }

    // Called when publishing has completed (all subscribers received the data) with the start time (for calculating the latency, statistics)
    def handler(start: Instant): ((Try[HttpResponse], Path)) => Unit = {
      case (Success(response), path) =>
        if (response.status == StatusCodes.Accepted) {
          if (stats) logStats(path, start) else log.info(s"Result for file: $path was successful")
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
    val uploader = new FileUploader(chunkSize)
    if (repeat) {
      // Note: Will never end: need to ^C to stop
      while (true) {
        Await.ready(uploader.uploadFiles(streamName, uri, paths, delay, handler(Instant.now)), Duration.Inf)
        if (delay != Duration.Zero) Thread.sleep(delay.toMillis)
      }
      Future.never.map(_ => Done)
    } else {
      uploader.uploadFiles(streamName, uri, paths, delay, handler(Instant.now))
    }
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
