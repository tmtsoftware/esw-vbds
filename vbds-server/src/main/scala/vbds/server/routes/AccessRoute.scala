package vbds.server.routes

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives
import akka.stream._
import akka.stream.scaladsl.{MergeHub, Sink}
import akka.util.ByteString
import vbds.server.actors.{AccessApi, AdminApi}
import vbds.server.models.JsonSupport


/**
  * Provides the HTTP route for the VBDS Access Service.
  *
  * @param adminData used to access the distributed list of streams (using cluster + CRDT)
  */
class AccessRoute(adminData: AdminApi, accessData: AccessApi)(implicit val system: ActorSystem, implicit val mat: ActorMaterializer)
  extends Directives with JsonSupport {

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName

    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  val log = Logging(system, this)

  val route =
    pathPrefix("vbds" / "access" / "streams") {
      // List all streams: Response: OK: Stream names in JSON; empty document if no streams
      get {
        onSuccess(adminData.listStreams()) { streams =>
          Cors.cors(complete(streams))
        }
        // Create a stream subscription: Response: SwitchingProtocols - Creates a websocket connection to the Access Service
        path(Remaining) { name =>
          log.debug(s"subscribe to stream: $name")
          onSuccess(adminData.streamExists(name)) { exists =>
            if (exists) {
              log.debug(s"subscribe to existing stream: $name")

              // We need a Source for writing to the websocket, but we want a Sink:
              // This provides a Sink that feeds the Source.
              val (sink, source) = MergeHub.source[ByteString].preMaterialize()
              val id = UUID.randomUUID().toString

              // Remove any subscriber that disconnects
              val inSink = Sink.onComplete[Message] { _ =>
                log.info(s"Deleting subscription with id $id after client closed websocket connection")
                accessData.deleteSubscription(id)
              }

              onSuccess(accessData.addSubscription(name, id, sink)) { _ =>
                extractUpgradeToWebSocket { upgrade =>
                  complete(upgrade.handleMessagesWithSinkSource(inSink, source.map(BinaryMessage(_))))
                }
              }
            } else {
              complete(StatusCodes.NotFound -> s"The stream $name does not exists")
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
                  complete(StatusCodes.Accepted)
                }
              } else {
                complete(
                  StatusCodes.NotFound -> s"The subscription with the id $id does not exist")
              }
            }

          }
        }
    }
}
