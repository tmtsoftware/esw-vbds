package vbds.server.routes

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import vbds.server.actors.{AccessApi, AdminApi, TransferApi}
import vbds.server.models.JsonSupport
import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import vbds.server.marshalling.BinaryMarshallers
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}

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
//                    val x = Await.result(byteStrings.runWith(Sink.seq), 3.seconds)
//                    x.foreach(bs => log.info(s"XXXXXXXXXXXXXXX Seq bs size: ${bs.size}"))
//                    val y = Source(x)
//                    y
                    val producer = byteStrings.toMat(BroadcastHub.sink(1))(Keep.right).run()
//                    val producer = y.toMat(BroadcastHub.sink)(Keep.right).run()
//                    Await.ready(producer.runForeach(bs => log.info(s"XXXXXXXXXXXXXXXXx Producer bs size: ${bs.size}")), 3.seconds)
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
            onSuccess(adminApi.streamExists(streamName)) { exists =>
              if (exists) {
                extractDataBytes { byteStrings =>
                  onSuccess(transferApi.publish(streamName, byteStrings, dist = false)) { _ =>
                    complete(Accepted)
                  }
                }
              } else {
                complete(BadRequest -> s"The stream $streamName does not exists")
              }
            }
          }
        }
      }

}
