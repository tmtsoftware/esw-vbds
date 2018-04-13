package vbds.server.controllers

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.FileIO
import vbds.server.actors.AdminData
import vbds.server.models.JsonSupport
import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import vbds.server.marshalling.BinaryMarshallers

/**
  * Provides the HTTP route for the VBDS Transfer Service.
  *
  * @param adminData used to access the distributed list of streams (using cluster + CRDT)
  */
class TransferRoute(adminData: AdminData)(implicit val system: ActorSystem,
                                          materializer: ActorMaterializer)
    extends Directives
    with JsonSupport
    with BinaryMarshallers {

  import system.dispatcher

  val transferDir = "/tmp" // XXX TODO FIXME

  def copyFile(name: String, byteArrays: Source[ByteString, Any]) = {
    val file = new File(s"$transferDir/$name")
    println(s"writing to $file")
    byteArrays.runWith(FileIO.toPath(file.toPath)).map(_ => ())
  }

  val route =
    pathPrefix("vbds" / "transfer" / "streams") {
      // List all streams: Response: OK: Stream names and descriptions in JSON; empty document if no streams
      get {
        onSuccess(adminData.listStreams()) { streams =>
          complete(streams)
        }
      } ~
        // Publish an image to a stream, Response: 204 – Success (no content) 400 – Bad Request (non- existent stream)
        post {
          path(Remaining) { name =>
            onSuccess(adminData.streamExists(name)) { exists =>
              if (exists) {
                entity(as[Source[ByteString, Any]]) { byteStrings =>
                  onSuccess(copyFile(name, byteStrings)) {
                    complete(Accepted)
                  }
                }
              } else {
                complete(NotFound -> s"The stream $name does not exists")
              }
            }
          }
        }
    }
}
