package vbds.server.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import vbds.server.actors.SharedDataActor.Publish

import scala.concurrent.Future

trait TransferApi {
  def publish(streamName: String,
              byteArrays: Source[ByteString, Any]): Future[Unit]
}

class TransferApiImpl(sharedDataActor: ActorRef, accessApi: AccessApi)(
    implicit system: ActorSystem)
    extends TransferApi {

  import system.dispatcher

  def publish(streamName: String,
              byteArrays: Source[ByteString, Any]): Future[Unit] = {
    accessApi.listSubscriptions().map { subscriptions =>
      val set = subscriptions.filter(_.streamName == streamName)
      sharedDataActor ! Publish(set, byteArrays)
    }
  }
}
