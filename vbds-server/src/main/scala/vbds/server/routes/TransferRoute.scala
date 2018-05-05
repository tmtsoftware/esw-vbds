package vbds.server.routes

import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import vbds.server.actors.{AccessApi, AdminApi, TransferApi}
import vbds.server.models.JsonSupport
import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import vbds.server.marshalling.BinaryMarshallers
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Provides the HTTP route for the VBDS Transfer Service.
 *
 * @param adminApi used to access the distributed list of streams (using cluster + CRDT)
 */
class TransferRoute(adminApi: AdminApi, accessApi: AccessApi, transferApi: TransferApi)(implicit val system: ActorSystem,
                                                                                        implicit val mat: Materializer)
    extends Directives
    with JsonSupport
    with BinaryMarshallers {

  import system.dispatcher

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName

    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  val log = Logging(system, this)

  private def broadcast(): (Sink[String, NotUsed], Source[String, NotUsed]) = {
      MergeHub.source[String](perProducerBufferSize = 16)
        .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
        .run()
  }

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
            fileUpload("data") {
              case (_, byteStrings) =>
                val producer = byteStrings.toMat(BroadcastHub.sink(1))(Keep.right).run()
                onSuccess(transferApi.publish(streamName, producer, dist = true)) { _ =>
                  complete(Accepted)
                }
            }
          } else {
            complete(BadRequest -> s"The stream $streamName does not exists")
          }
        }
      }
    }
  } ~
  pathPrefix("vbds" / "transfer" / "internal") {
    // Internal API: Distribute an image to another server, Response: 204 – Success (no content) or 400 – Bad Request (non- existent stream)
    post {
      path(Remaining) { streamName =>
        withoutSizeLimit {
          extractDataBytes { byteStrings =>
            val producer = byteStrings.toMat(BroadcastHub.sink(1))(Keep.right).run()
            producer.runForeach(bs => log.info(s"WWWWWWWWWWWWW bs size = ${bs.size}"))
            onSuccess(transferApi.publish(streamName, producer, dist = false)) { _ =>
              complete(Accepted)
            }
          }

        }
      }
    }
  }

}
