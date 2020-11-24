package vbds.server.actors

import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import vbds.server.actors.SharedDataActor.SharedDataActorMessages
import vbds.server.models.StreamInfo

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._

/**
  * Internal Admin API
  */
trait AdminApi {

  def listStreams(): Future[Set[StreamInfo]]

  def streamExists(name: String): Future[Boolean]

  def addStream(name: String, contentType: String): Future[StreamInfo]

  def deleteStream(name: String): Future[StreamInfo]
}

/**
  * A wrapper around the admin API of the shared data actor.
  */
class AdminApiImpl(sharedDataActor: ActorRef[SharedDataActorMessages])(implicit scheduler: Scheduler, ec: ExecutionContext, timeout: Timeout = Timeout(3.seconds))
    extends AdminApi {

  import SharedDataActor._

  def listStreams(): Future[Set[StreamInfo]] = {
    sharedDataActor.ask(ListStreams)
  }

  def streamExists(name: String): Future[Boolean] = {
    listStreams().map(_.exists(_.name == name))
  }

  def addStream(name: String, contentType: String): Future[StreamInfo] = {
    sharedDataActor.ask(AddStream(name, contentType, _))
  }

  def deleteStream(name: String): Future[StreamInfo] = {
    sharedDataActor.ask(DeleteStream(name, _))
  }
}
