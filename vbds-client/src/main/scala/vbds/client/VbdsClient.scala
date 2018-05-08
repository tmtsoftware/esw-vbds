package vbds.client

import java.io.File
import java.nio.file.Path

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.Materializer

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}

class VbdsClient(host: String, port: Int, chunkSize: Int = 1024 * 1024)(implicit val system: ActorSystem, implicit val mat: Materializer) {

  implicit val executionContext = system.dispatcher
  val adminRoute                = "/vbds/admin/streams"
  val accessRoute               = "/vbds/access/streams"
  val transferRoute             = "/vbds/transfer/streams"
  val maxFrameLengthBytes       = 1024 * 1024

  def createStream(streamName: String): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"http://$host:$port$adminRoute/$streamName"))
  }

  def listStreams(): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(uri = s"http://$host:$port$adminRoute"))
  }

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
    // XXX FIXME TODO: Make this a parameter
    val handler: ((Try[HttpResponse], Path)) => Unit = {
      case (Success(response), path) =>
        // TODO: also check for response status code
        println(s"Result for file: $path was successful: $response")
        response.discardEntityBytes() // don't forget this
      case (Failure(ex), path) =>
        println(s"Uploading file $path failed with $ex")
    }

    val uri = s"http://$host:$port$transferRoute/$streamName"
    val paths = if (file.isDirectory) {
      file.listFiles().map(_.toPath).toList.sortWith {
        case (p1, p2) => comparePaths(p1, p2)

      }
    } else {
      List(file.toPath)
    }
    val uploader = new FileUploader(chunkSize)
    uploader.uploadFiles(streamName, uri, paths, delay, handler)
  }

  val fileNamePattern = """\d+""".r

  def comparePaths(p1: Path, p2: Path): Boolean = {
    val s1 = p1.getFileName.toString
    val s2 = p2.getFileName.toString
    val a = fileNamePattern.findAllIn(s1).toList.headOption.getOrElse("")
    val b = fileNamePattern.findAllIn(s2).toList.headOption.getOrElse("")
    if (a.nonEmpty && b.nonEmpty)
      a.toInt.compareTo(b.toInt) < 0
    else
      s1.compareTo(s2) < 0
  }

  // XXX TODO FIXME: Change action to general purpose handler
  def subscribe(streamName: String, dir: String, action: Option[String]): Future[HttpResponse] = {
    println(s"subscribe to $streamName")
    val receiver = system.actorOf(WebSocketActor.props(streamName, new File(dir), action.getOrElse("")))
    val wsListener = new WebSocketListener
    wsListener.subscribe(Uri(s"ws://$host:$port$accessRoute/$streamName"), receiver)
  }

}
