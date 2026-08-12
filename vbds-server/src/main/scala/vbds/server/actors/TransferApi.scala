package vbds.server.actors

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorRef, Scheduler}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.{ByteString, Timeout}
import vbds.server.actors.SharedDataActor.{Publish, SharedDataActorMessages}
import org.apache.pekko.actor.typed.scaladsl.AskPattern._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Internal data transfer API
 */
trait TransferApi {
  def publish(streamName: String, source: Source[ByteString, Any], dist: Boolean): Future[Done]
}

class TransferApiImpl(sharedDataActor: ActorRef[SharedDataActorMessages])(
    implicit scheduler: Scheduler,
    timeout: Timeout = 5.minutes
) extends TransferApi {

  def publish(streamName: String, source: Source[ByteString, Any], dist: Boolean): Future[Done] = {
    sharedDataActor.ask(Publish(streamName, source, dist, _))
  }
}
