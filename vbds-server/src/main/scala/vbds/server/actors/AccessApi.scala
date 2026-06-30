package vbds.server.actors

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.{ActorRef, Scheduler}
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import vbds.server.actors.SharedDataActor.SharedDataActorMessages
import vbds.server.models.AccessInfo
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import vbds.server.routes.AccessRoute.WebsocketResponseActorMsg

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * Internal API to manage VBDS subscriptions
 */
trait AccessApi {
  def addSubscription(streamName: String,
                      id: String,
                      sink: Sink[ByteString, NotUsed],
                      wsResponseActor: ActorRef[WebsocketResponseActorMsg]): Future[AccessInfo]

  def listSubscriptions(): Future[Set[AccessInfo]]

  def subscriptionExists(id: String): Future[Boolean]

  def deleteSubscription(id: String): Future[Unit]
}

/**
 * Uses the sharedDataActor to distribute subscription info, while saving the associated subscriber sinks locally in a map.
 */
class AccessApiImpl(sharedDataActor: ActorRef[SharedDataActorMessages])(implicit scheduler: Scheduler, ec: ExecutionContext, timeout: Timeout = Timeout(3.seconds))
    extends AccessApi {
  import SharedDataActor._

  def addSubscription(streamName: String,
                      id: String,
                      sink: Sink[ByteString, NotUsed],
                      wsResponseActor: ActorRef[WebsocketResponseActorMsg]): Future[AccessInfo] = {
    sharedDataActor.ask(AddSubscription(streamName, id, sink, wsResponseActor, _))
  }

  def listSubscriptions(): Future[Set[AccessInfo]] = {
    sharedDataActor.ask(ListSubscriptions.apply)
  }

  def subscriptionExists(id: String): Future[Boolean] = {
    listSubscriptions().map(_.exists(_.id == id))
  }

  def deleteSubscription(id: String): Future[Unit] = {
    sharedDataActor.ask(DeleteSubscription(id, _)).map(_ => ())
  }
}
