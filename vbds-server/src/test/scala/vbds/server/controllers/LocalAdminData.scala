package vbds.server.controllers

import akka.actor.ActorSystem
import vbds.server.actors.AdminData
import vbds.server.models.StreamInfo

import scala.concurrent.Future

// Dummy class that stores the data locally, just to test the route
class LocalAdminData(system: ActorSystem) extends AdminData {
  private var streams: Set[StreamInfo] = Set.empty

  def listStreams(): Future[Set[StreamInfo]] = {
    Future.successful(streams)
  }

  def streamExists(name: String): Future[Boolean] = {
    val result = streams.exists(_.name == name)
    Future.successful(result)
  }

  def addStream(name: String): Future[StreamInfo] = {
    val result = StreamInfo(name)
    streams = streams + result
    Future.successful(result)
  }

  def deleteStream(name: String): Future[StreamInfo] = {
    val result = StreamInfo(name)
    streams = streams - result
    Future.successful(result)
  }
}
