package vbds.client.app

import java.io.File
import java.nio.file.Path

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.Materializer

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}

class VbdsClient(host: String, port: Int, chunkSize: Int = 1024*1024)(implicit val system: ActorSystem, implicit val mat: Materializer) {

  implicit val executionContext = system.dispatcher
  val adminRoute = "/vbds/admin/streams"
  val accessRoute = "/vbds/access/streams"
  val transferRoute = "/vbds/transfer/streams"

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
      file.listFiles().map(_.toPath).toList
    } else {
      List(file.toPath)
    }
    val uploader = new FileUploader(chunkSize)
    uploader.uploadFiles(uri, paths, delay, handler)
  }


  // XXX TODO FIXME: Change action to general purpose handler
  def subscribe(streamName: String, action: Option[String]): Future[HttpResponse] = {
    println(s"XXX subscribe to $streamName")

    def handler(msg: Message): Unit = {
      msg match {
          // XXX TODO FIXME: consume binary message source
        case bm: BinaryMessage =>
          val byteStrings = bm.dataStream
          var x = 0
          println(s"\n\nXXX Received binary message (strict: ${bm.isStrict}): $bm")
          val f = byteStrings.runForeach(bs => {
            x = x + bs.size
            println(s"XXX received ${bs.size} bytes: ${bs.utf8String}")
          })
          f.onComplete(_ => println(s"XXX Total message size = $x bytes\n\n"))

        case x =>
          println(s"XXX Wrong message type: $x")
      }
    }

    val wsListener = new WebSocketListener
    wsListener.subscribe(Uri(s"ws://$host:$port$accessRoute/$streamName"), handler)
  }

}
