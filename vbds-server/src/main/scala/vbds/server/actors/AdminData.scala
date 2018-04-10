package vbds.server.actors

import vbds.server.models.StreamInfo

import scala.concurrent.Future

/**
  * Represents stream admin data shared between admin servers
  */
trait AdminData {

  def listStreams(): Future[Set[StreamInfo]]

  def streamExists(name: String): Future[Boolean]

  def addStream(name: String): Future[StreamInfo]

  def deleteStream(name: String): Future[StreamInfo]
}
