package vbds.server.routes

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import vbds.server.actors.{AccessApi, AdminApi, TransferApi}
import vbds.server.models.JsonSupport
import akka.stream.scaladsl.Source
import akka.util.ByteString

import akka.actor.ActorSystem
import vbds.server.marshalling.BinaryMarshallers

/**
  * Provides the HTTP route for the VBDS Transfer Service.
  *
  * @param adminApi used to access the distributed list of streams (using cluster + CRDT)
  */
class TransferRoute(adminApi: AdminApi,
                    accessApi: AccessApi,
                    transferApi: TransferApi)(implicit val system: ActorSystem)
    extends Directives
    with JsonSupport
    with BinaryMarshallers {

  val route =
    pathPrefix("vbds" / "transfer" / "streams") {
      // List all streams: Response: OK: Stream names and descriptions in JSON; empty document if no streams
      get {
        onSuccess(adminApi.listStreams()) { streams =>
          complete(streams)
        }
      } ~
        // Publish an image to a stream, Response: 204 – Success (no content) or 400 – Bad Request (non- existent stream)
        post {
          path(Remaining) { streamName =>
            onSuccess(adminApi.streamExists(streamName)) { exists =>
              if (exists) {
                entity(as[Source[ByteString, Any]]) { byteStrings =>
                  onSuccess(transferApi.publish(streamName, byteStrings)) {
                    complete(Accepted)
                  }
                }
              } else {
                complete(
                  BadRequest -> s"The stream $streamName does not exists")
              }
            }
          }
        }
    }
}
