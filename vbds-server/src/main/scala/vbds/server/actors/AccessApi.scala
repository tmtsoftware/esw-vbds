package vbds.server.actors

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import akka.pattern.ask
import akka.util.Timeout
import vbds.server.models.AccessInfo

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Internal API to manage VBDS subscriptions
 */
trait AccessApi {
  def addSubscription(streamName: String, sink: Sink[ByteString, NotUsed]): Future[AccessInfo]

  def listSubscriptions(): Future[Set[AccessInfo]]

  def subscriptionExists(id: String): Future[Boolean]

  def deleteSubscription(streamName: String): Future[Unit]
}

/**
 * Uses the sharedDataActor to distribute subscription info, while saving the associated subscriber sinks locally in a map.
 */
class AccessApiImpl(sharedDataActor: ActorRef)(implicit system: ActorSystem, timeout: Timeout = Timeout(3.seconds))
    extends AccessApi {
  import SharedDataActor._
  import system.dispatcher

  def addSubscription(streamName: String, sink: Sink[ByteString, NotUsed]): Future[AccessInfo] = {
    (sharedDataActor ? AddSubscription(streamName, sink)).mapTo[AccessInfo]
  }

  def listSubscriptions(): Future[Set[AccessInfo]] = {
    (sharedDataActor ? ListSubscriptions).mapTo[Set[AccessInfo]]
  }

  def subscriptionExists(id: String): Future[Boolean] = {
    listSubscriptions().map(_.exists(_.id == id))
  }

  def deleteSubscription(id: String): Future[Unit] = {
    listSubscriptions().map(s => s.find(_.id == id)).map { maybeInfo =>
      maybeInfo.foreach { info =>
        (sharedDataActor ? DeleteSubscription(info)).mapTo[AccessInfo]
      }
    }
  }
}
