package vbds.server.routes

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import vbds.server.actors.{AdminApi, TransferApi}
import vbds.server.models.JsonSupport
import akka.event.{LogSource, Logging}
import vbds.server.marshalling.BinaryMarshallers
import akka.stream.scaladsl.Source
import akka.util.ByteString
import vbds.server.actors.SharedDataActor.SharedDataActorMessages

/**
 * Provides the HTTP route for the VBDS Transfer Service.
 *
 * @param adminApi used to access the distributed list of streams (using cluster + CRDT)
 */
class TransferRoute(adminApi: AdminApi,
                    transferApi: TransferApi,
                    ctx: ActorContext[SharedDataActorMessages])
    extends Directives
    with JsonSupport
    with BinaryMarshallers {

  implicit val actorSystem = ctx.system

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName

    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  val log = Logging(ctx.system.classicSystem, this)

  val route =
  pathPrefix("vbds" / "transfer" / "streams") {
    // List all streams: Response: OK: Stream names in JSON; empty document if no streams
    get {
      onSuccess(adminApi.listStreams()) { streams =>
        Cors.cors(complete(streams))
      }
    } ~
    // Publish a data file to a stream, Response: 204 – Success (no content) or 400 – Bad Request (non- existent stream)
    post {
      path(Remaining) { streamName =>
        onSuccess(adminApi.streamExists(streamName)) { exists =>
          if (exists) {
            fileUpload("data") {
              case (_, source) =>
                // Add a ByteString containing a single newline to mark the end of the stream for one data file
                val terminatedSource = source.concat(Source.single(ByteString("\n")))
                onSuccess(transferApi.publish(streamName, terminatedSource, dist = true)) { _ =>
                  Cors.cors(complete(Accepted))
                }
            }
          } else {
            extractRequest { r =>
              r.discardEntityBytes()
              Cors.cors(complete(BadRequest -> s"The stream $streamName does not exists"))
            }
          }
        }
      }
    }
  } ~
  pathPrefix("vbds" / "transfer" / "internal") {
    // Internal API: Distribute a data file to another server, Response: 204 – Success (no content) or 400 – Bad Request (non- existent stream)
    post {
      path(Remaining) { streamName =>
        withoutSizeLimit {
          extractDataBytes { source =>
            onSuccess(transferApi.publish(streamName, source, dist = false)) { _ =>
              Cors.cors(complete(Accepted))
            }
          }

        }
      }
    }
  }

}
