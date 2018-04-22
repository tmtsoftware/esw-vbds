package vbds.server.routes

import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.server.Directives
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import vbds.server.actors.{AccessApi, AdminApi}
import vbds.server.models.JsonSupport

/**
  * Provides the HTTP route for the VBDS Access Service.
  *
  * @param adminData used to access the distributed list of streams (using cluster + CRDT)
  */
class AccessRoute(adminData: AdminApi, accessData: AccessApi)(implicit mat: ActorMaterializer)
    extends Directives with JsonSupport with CustomDirectives {

  // handleWebSocket requires a source, and we need a sink to write the images to
  // See https://discuss.lightbend.com/t/create-source-from-sink-and-vice-versa/605
  private def getWsSourceSink: (Source[ByteString, NotUsed], Sink[ByteString, NotUsed]) = {
    val in = Sink.asPublisher[ByteString](fanout = false)
    val out = Source.asSubscriber[ByteString]

    val (source, sink) =
      out
        .toMat(in)(Keep.both)
        .mapMaterializedValue {
          case (sub, pub) =>
            (Source.fromPublisher(pub), Sink.fromSubscriber(sub))
        }
        .run()
    (source, sink)
  }

  val route =
    pathPrefix("vbds" / "access" / "streams") {
      // List all streams: Response: OK: Stream names and descriptions in JSON; empty document if no streams
      get {
        onSuccess(adminData.listStreams()) { streams =>
          complete(streams)
        }
      } ~
        // Create a stream subscription: Response: OK - [Stream name and any other details returned as JSON???]; Creates a websocket connection to the Access Service
        post {
          path(Remaining) { name =>
            onSuccess(adminData.streamExists(name)) { exists =>
              if (exists) {
                val (source, sink) = getWsSourceSink
                onSuccess(accessData.addSubscription(name, sink)) { info =>
                  handleWebsocketMessages(Sink.ignore, source.map(BinaryMessage(_))) ~
                    complete(info)
                }
              } else {
                complete(NotFound -> s"The stream $name does not exists")
              }
            }
          }
        } ~
        // Deletes a stream subscription: Response: 204 – Success (no content) or 404 – Subscription not found
        delete {
          path(Remaining) { id => // id returned as part of AccessData response to subscription request
            onSuccess(accessData.subscriptionExists(id)) { exists =>
              if (exists) {
                onSuccess(accessData.deleteSubscription(id)) {
                  complete(Accepted)
                }
              } else {
                complete(
                  NotFound -> s"The subscription with the id $id does not exist")
              }
            }

          }
        }
    }
}
