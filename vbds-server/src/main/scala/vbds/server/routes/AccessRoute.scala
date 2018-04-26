package vbds.server.routes

import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.server.Directives
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
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
      // List all streams: Response: OK: Stream names and descriptions in JSON; empty document if no streams
      get {
        onSuccess(adminData.listStreams()) { streams =>
          complete(streams)
        }
        // Create a stream subscription: Response: OK - Creates a websocket connection to the Access Service
        path(Remaining) { name =>
          log.info(s"XXX subscribe to $name")
          onSuccess(adminData.streamExists(name)) { exists =>
            if (exists) {
              log.info(s"XXX subscribe to $name exists")

              val (queue, source) = Source.queue[ByteString](10, OverflowStrategy.backpressure).preMaterialize

              onSuccess(accessData.addSubscription(name, queue)) { info =>
                log.info(s"XXX subscribe to $name info: $info")
                extractUpgradeToWebSocket { upgrade =>
                  log.info(s"XXX subscribe to $name extractUpgradeToWebSocket: $extractUpgradeToWebSocket")
                  complete(upgrade.handleMessagesWithSinkSource(Sink.ignore, source.map(BinaryMessage(_))))
                }
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
