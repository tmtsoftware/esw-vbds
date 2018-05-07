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
  def publish(streamName: String, source: Source[ByteString, Any], dist: Boolean): Future[Done]
}

class TransferApiImpl(sharedDataActor: ActorRef, accessApi: AccessApi)(implicit val system: ActorSystem,
                                                                       implicit val mat: Materializer,
                                                                       implicit val timeout: Timeout = 60.seconds)
    extends TransferApi {

  import system.dispatcher

  def publish(streamName: String, source: Source[ByteString, Any], dist: Boolean): Future[Done] = {
    accessApi.listSubscriptions().flatMap { subscriptions =>
      val subscribers = subscriptions.filter(_.streamName == streamName)
      if (subscribers.nonEmpty) {
        (sharedDataActor ? Publish(streamName, subscribers, source, dist)).mapTo[Done]
      } else {
        Future.successful(Done)
      }
    }
  }
}
