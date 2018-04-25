package vbds.client.app

import java.nio.file.Path

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class FileUploader(implicit val system: ActorSystem, implicit val materializer: Materializer) {

  import system.dispatcher

  private def poolClientFlow(uri: Uri) = {
    println(s"XXX Pool client flow ${uri.authority.host.address()}, ${uri.authority.port}")
    Http().cachedHostConnectionPool[Path](uri.authority.host.address(), uri.authority.port)
  }

  private def createUploadRequest(uri: Uri, path: Path): Future[(HttpRequest, Path)] = {
    // XXX TODO FIXME: Add chunkSize param
    val bodyPart = FormData.BodyPart.fromPath(path.toFile.getName, ContentTypes.`application/octet-stream`, path)

    val body = FormData(bodyPart) // only one file per upload
    Marshal(body).to[RequestEntity].map { entity => // use marshalling to create multipart/formdata entity
      // build the request and annotate it with the original metadata
      HttpRequest(method = HttpMethods.POST, uri = uri, entity = entity) -> path
    }
  }

  /**
    * Uploads the given files to the given URI, one after the other
    * @param uri the URI for the HTTP server route
    * @param files the files to upload
    * @param delay optional delay between uploads
    * @param handler called with the results
    * @return completes when done
    */
  def uploadFiles(uri: Uri, files: List[Path], delay: FiniteDuration, handler: ((Try[HttpResponse], Path)) => Unit): Future[Done] = {
    Source(files)
      .delay(delay)
      .mapAsync(1)(path => createUploadRequest(uri, path))
      .via(poolClientFlow(uri))
      .runForeach(handler)
//        case (Success(response), path) =>
//          // XXX TODO: also check for response status code
//          println(s"Result for file: $path was successful: $response")
//          response.discardEntityBytes() // don't forget this
//        case (Failure(ex), path) =>
//          println(s"Uploading file $path failed with $ex")

  }
}
