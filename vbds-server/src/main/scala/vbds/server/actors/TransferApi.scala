package vbds.server.actors

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import vbds.server.actors.SharedDataActor.Publish

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Internal data transfer API
  */
trait TransferApi {
  def publish(streamName: String, byteArrays: Source[ByteString, Any]): Future[Unit]
}

class TransferApiImpl(sharedDataActor: ActorRef, accessApi: AccessApi)
                     (implicit val system: ActorSystem, implicit val timeout: Timeout = 60.seconds)
  extends TransferApi {

  import system.dispatcher

  def publish(streamName: String, byteArrays: Source[ByteString, Any]): Future[Unit] = {
    accessApi.listSubscriptions().flatMap { subscriptions =>
      val set = subscriptions.filter(_.streamName == streamName)
      (sharedDataActor ? Publish(set, byteArrays)).mapTo[Done].map(_ => ())
    }
  }
}
