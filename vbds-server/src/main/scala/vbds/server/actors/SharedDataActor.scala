package vbds.server.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{ORSet, ORSetKey}
import akka.cluster.ddata.Replicator._
import vbds.server.models.StreamInfo

object SharedDataActor {

  case class AddStream(name: String)

  case class DeleteStream(name: String)

  case object ListStreams

  def props(replicator: ActorRef)(implicit cluster: Cluster): Props =
    Props(new SharedDataActor(replicator))
}

class SharedDataActor(replicator: ActorRef)(implicit cluster: Cluster)
    extends Actor
    with ActorLogging {

  import SharedDataActor._

  val AdminDataKey = ORSetKey[StreamInfo]("streamInfo")

  replicator ! Subscribe(AdminDataKey, self)

  def receive = {
    case AddStream(name) =>
      log.info("Adding: {}", name)
      val info = StreamInfo(name)
      replicator ! Update(AdminDataKey, ORSet.empty[StreamInfo], WriteLocal)(
        _ + info)
      sender() ! info

    case DeleteStream(name) =>
      log.info("Removing: {}", name)
      val info = StreamInfo(name)
      replicator ! Update(AdminDataKey, ORSet.empty[StreamInfo], WriteLocal)(
        _ - info)
      sender() ! info

    case ListStreams =>
      // incoming request to retrieve current value of the counter
      replicator ! Get(AdminDataKey, ReadLocal, request = Some(sender()))

    case g @ GetSuccess(AdminDataKey, Some(replyTo: ActorRef)) ⇒
      val value = g.get(AdminDataKey).elements
      replyTo ! value

    case GetFailure(AdminDataKey, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case NotFound(AdminDataKey, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case _: UpdateResponse[_] ⇒ // ignore

    case c @ Changed(AdminDataKey) ⇒
      val data = c.get(AdminDataKey)
      log.info("Current streams: {}", data.elements)
  }

  //  override def postStop(): Unit = ???

}
