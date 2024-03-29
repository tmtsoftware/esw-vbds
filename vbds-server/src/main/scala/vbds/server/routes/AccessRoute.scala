package vbds.server.routes

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives
import akka.util.ByteString
import vbds.server.actors.{AccessApi, AdminApi}
import vbds.server.models.JsonSupport
import AccessRoute._
import akka.stream.scaladsl.{Flow, MergeHub, Sink}
import vbds.server.actors.AkkaTypedExtension.UserActorFactory

// Actor to handle ACK responses from websocket clients
object AccessRoute {

  sealed trait WebsocketResponseActorMsg

  // Responds with Ack if there is a response from the ws client
  final case class Get(replyTo: ActorRef[Ack.type]) extends WebsocketResponseActorMsg

  // Says there was a response from the ws client
  final case object Put extends WebsocketResponseActorMsg

  // Stops the actor
  final case object Stop extends WebsocketResponseActorMsg

  // Reponse to Get message
  final case object Ack

  // Actor that handles responses from the websocket
  private def websocketResponseBehavior(
      responses: Int = 1,
      senders: List[ActorRef[Ack.type]] = Nil
  ): Behavior[WebsocketResponseActorMsg] = {
    Behaviors.receive { (_, message) =>
      message match {
        case Put =>
          if (senders.nonEmpty) {
            senders.last ! Ack
            websocketResponseBehavior(responses, senders.dropRight(1))
          } else {
            websocketResponseBehavior(responses + 1, Nil)
          }

        case Get(replyTo) =>
          if (responses > 0) {
            replyTo ! Ack
            websocketResponseBehavior(responses - 1, senders)
          } else {
            websocketResponseBehavior(0, replyTo :: senders)
          }

        case Stop =>
          Behaviors.stopped
      }
    }
  }
}

/**
 * Provides the HTTP route for the VBDS Access Service.
 *
 * @param adminData used to access the distributed list of streams (using cluster + CRDT)
 */
class AccessRoute(adminData: AdminApi, accessData: AccessApi)(implicit val actorSystem: ActorSystem[SpawnProtocol.Command])
    extends Directives
    with JsonSupport
    with LoggingSupport {

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
              val (sink, source)  = MergeHub.source[ByteString](1).preMaterialize()
              val id              = UUID.randomUUID().toString
              val wsResponseActor = actorSystem.spawn(websocketResponseBehavior(), "wsResponseActor")

              // Input from client ws
              val inSink = Flow[Message]
                .map { msg =>
                  // Notify this actor that the ws client responded, so that the publisher can check it
                  wsResponseActor ! AccessRoute.Put
                  msg
                }
                .to(Sink.onComplete[Message] { _ =>
                  log.debug(s"Deleting subscription with id $id after client closed websocket connection")
                  accessData.deleteSubscription(id)
                  wsResponseActor ! AccessRoute.Stop
                })

              onSuccess(accessData.addSubscription(name, id, sink, wsResponseActor)) { _ =>
                extractWebSocketUpgrade { upgrade =>
                  Cors.cors(complete(upgrade.handleMessagesWithSinkSource(inSink, source.map(BinaryMessage(_)))))
                }
              }
            } else {
              log.error(s"The stream $name does not exists")
              Cors.cors(complete(StatusCodes.NotFound -> s"The stream $name does not exists"))
            }
          }
        }
      }
      // ~
      // Deletes a stream subscription: Response: 204 – Success (no content) or 404 – Subscription not found
//      delete {
//        path(Remaining) { id => // id returned as part of AccessData response to subscription request
//          onSuccess(accessData.subscriptionExists(id)) { exists =>
//            if (exists) {
//              onSuccess(accessData.deleteSubscription(id)) {
//                Cors.cors(complete(StatusCodes.Accepted))
//              }
//            } else {
//              Cors.cors(complete(StatusCodes.NotFound -> s"The subscription with the id $id does not exist"))
//            }
//          }
//
//        }
//      }
    }
}
