package vbds.client

import java.nio.file.Path

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model._
import akka.stream.{Materializer, ThrottleMode}
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class FileUploader(chunkSize: Int = 1024 * 1024)(implicit val system: ActorSystem, implicit val materializer: Materializer) {

  import system.dispatcher

  private def poolClientFlow(uri: Uri) = {
    Http().cachedHostConnectionPool[Path](uri.authority.host.address(), uri.authority.port)
  }

  private def createUploadRequest(streamName: String, uri: Uri, path: Path): Future[(HttpRequest, Path)] = {
    val bodyPart = FormData.BodyPart.fromPath("data", ContentTypes.`application/octet-stream`, path, chunkSize)
    val body     = FormData(bodyPart) // only one file per upload
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
  def uploadFiles(streamName: String,
                  uri: Uri,
                  files: List[Path],
                  delay: FiniteDuration,
                  handler: ((Try[HttpResponse], Path)) => Unit): Future[Done] = {
    def upload(source: Source[Path, NotUsed]): Future[Done] = {
      source
        .mapAsync(1)(path => createUploadRequest(streamName, uri, path))
        .via(poolClientFlow(uri))
        .runForeach(handler)
    }
    val source = Source(files)
    if (delay != Duration.Zero)
      upload(source.throttle(1, delay, 1, ThrottleMode.Shaping))
    else upload(source)
  }
}
