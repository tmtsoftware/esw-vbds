package vbds.server.routes

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import vbds.server.actors.{AccessApi, AdminApi, TransferApi}
import vbds.server.models.JsonSupport
import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import vbds.server.marshalling.BinaryMarshallers
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.stream.scaladsl.Source
import akka.util.ByteString


/**
  * Provides the HTTP route for the VBDS Transfer Service.
  *
  * @param adminApi used to access the distributed list of streams (using cluster + CRDT)
  */
class TransferRoute(adminApi: AdminApi, accessApi: AccessApi, transferApi: TransferApi)(implicit val system: ActorSystem)
  extends Directives
    with JsonSupport
    with BinaryMarshallers {

  import system.dispatcher

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName

    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  val log = Logging(system, this)

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
                log.info(s"XXX publish $streamName exists")

                fileUpload(streamName) {
                  case (metadata, byteStrings) =>
                    log.info(s"XXX publish $streamName")
                    val f = transferApi.publish(streamName, byteStrings, dist = true)
                    onSuccess(f) { _ =>
                      f.foreach(_ => log.info(s"XXX publish $streamName accepted/completed"))
                      complete(f.map(_ => Accepted))
                    }
                }
              } else {
                complete(
                  BadRequest -> s"The stream $streamName does not exists")
              }
            }
          }
        }
    } ~
      pathPrefix("vbds" / "transfer" / "internal") {
        // Internal API: Distribute an image to another server, Response: 204 – Success (no content) or 400 – Bad Request (non- existent stream)
        post {
          path(Remaining) { streamName =>
            onSuccess(adminApi.streamExists(streamName)) { exists =>
              if (exists) {
                log.info(s"XXX publish $streamName exists")
                extractDataBytes { byteStrings =>
                  val f = transferApi.publish(streamName, byteStrings, dist = false)
                  onSuccess(f) { _ =>
                    f.foreach(_ => log.info(s"XXX distribute $streamName accepted/completed"))
                    complete(f.map(_ => Accepted))
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
