package vbds.server.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import vbds.server.models.StreamInfo

import scala.concurrent.Future
import scala.concurrent.duration._

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
class AdminApiImpl(sharedDataActor: ActorRef)(implicit system: ActorSystem,
                                              timeout: Timeout = Timeout(
                                                3.seconds))
    extends AdminApi {

  import SharedDataActor._
  import system.dispatcher

  def listStreams(): Future[Set[StreamInfo]] = {
    (sharedDataActor ? ListStreams).mapTo[Set[StreamInfo]]
  }

  def streamExists(name: String): Future[Boolean] = {
    listStreams().map(_.exists(_.name == name))
  }

  def addStream(name: String, contentType: String): Future[StreamInfo] = {
    (sharedDataActor ? AddStream(name, contentType)).mapTo[StreamInfo]
  }

  def deleteStream(name: String): Future[StreamInfo] = {
    (sharedDataActor ? DeleteStream(name)).mapTo[StreamInfo]
  }
}
