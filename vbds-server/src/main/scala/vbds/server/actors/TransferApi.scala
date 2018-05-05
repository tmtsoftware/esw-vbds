package vbds.server.actors

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import akka.stream.Materializer
import vbds.server.actors.SharedDataActor.Publish

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Internal data transfer API
 */
trait TransferApi {
  def publish(streamName: String, producer: Source[ByteString, Any], dist: Boolean): Future[Done]
}

class TransferApiImpl(sharedDataActor: ActorRef, accessApi: AccessApi)(implicit val system: ActorSystem,
                                                                       implicit val mat: Materializer,
                                                                       implicit val timeout: Timeout = 60.seconds)
    extends TransferApi {

  import system.dispatcher

  def publish(streamName: String, producer: Source[ByteString, Any], dist: Boolean): Future[Done] = {

    // XXX TODO: Handle message framing!!!

    //    val x = byteArrays.via(Framing.delimiter(ByteString("\n"),
    //      maximumFrameLength = 256,
    //      allowTruncation = true))

    accessApi.listSubscriptions().flatMap { subscriptions =>
      val set = subscriptions.filter(_.streamName == streamName)
      if (set.nonEmpty) {
        (sharedDataActor ? Publish(streamName, set, producer, dist)).mapTo[Done]
      } else {
        Future.successful(Done)
      }
    }
  }
}
