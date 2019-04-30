package vbds.server.actors

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import akka.util.Timeout
import vbds.server.actors.SharedDataActor.SharedDataActorMessages
import vbds.server.models.AccessInfo
import vbds.server.routes.AccessRoute.WebsocketResponseActor.WebsocketResponseActorMsg

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Internal API to manage VBDS subscriptions
 */
trait AccessApi {
  def addSubscription(streamName: String, id: String, sink: Sink[ByteString, NotUsed], wsResponseActor: ActorRef[_]): Future[AccessInfo]

  def listSubscriptions(): Future[Set[AccessInfo]]

  def subscriptionExists(id: String): Future[Boolean]

  def deleteSubscription(id: String): Future[Unit]
}

/**
 * Uses the sharedDataActor to distribute subscription info, while saving the associated subscriber sinks locally in a map.
 */
class AccessApiImpl(sharedDataActor: ActorRef[SharedDataActorMessages])(implicit system: ActorSystem[_], timeout: Timeout = Timeout(3.seconds))
    extends AccessApi {
  import SharedDataActor._
  import system.dispatcher

  def addSubscription(streamName: String, id: String, sink: Sink[ByteString, NotUsed], wsResponseActor: ActorRef[WebsocketResponseActorMsg]): Future[AccessInfo] = {
    (sharedDataActor ? AddSubscription(streamName, id, sink, wsResponseActor)).mapTo[AccessInfo]
  }

  def listSubscriptions(): Future[Set[AccessInfo]] = {
    (sharedDataActor ? ListSubscriptions).mapTo[Set[AccessInfo]]
  }

  def subscriptionExists(id: String): Future[Boolean] = {
    listSubscriptions().map(_.exists(_.id == id))
  }

  def deleteSubscription(id: String): Future[Unit] = {
    (sharedDataActor ? DeleteSubscription(id)).map(_ => ())
  }
}
