package vbds.server.routes

import akka.actor.ActorSystem
import vbds.server.actors.AdminApi
import vbds.server.models.StreamInfo

import scala.concurrent.Future

// Dummy class that stores the data locally, just to test the route
class LocalAdminApi(system: ActorSystem) extends AdminApi {
  private var streams: Set[StreamInfo] = Set.empty

  def listStreams(): Future[Set[StreamInfo]] = {
    Future.successful(streams)
  }

  def streamExists(name: String): Future[Boolean] = {
    val result = streams.exists(_.name == name)
    Future.successful(result)
  }

  def addStream(name: String, contentType: String): Future[StreamInfo] = {
    val result = StreamInfo(name, contentType)
    streams = streams + result
    Future.successful(result)
  }

  def deleteStream(name: String): Future[StreamInfo] = {
    val result = streams.find(_.name == name).getOrElse(StreamInfo(name, ""))
    streams = streams - result
    Future.successful(result)
  }
}
