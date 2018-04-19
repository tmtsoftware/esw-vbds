package vbds.server.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{ORSet, ORSetKey}
import akka.cluster.ddata.Replicator._
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import vbds.server.models.{AccessInfo, StreamInfo}

object SharedDataActor {

  case class AddStream(streamName: String)

  case class DeleteStream(streamName: String)

  case object ListStreams

  // Sets the host and port that the http server is listening on
  case class LocalAddress(a: InetSocketAddress)

  case class AddSubscription(streamName: String,
                             sink: Sink[ByteString, NotUsed])

  case class DeleteSubscription(info: AccessInfo)

  case object ListSubscriptions

  case class Publish(set: Set[AccessInfo], byteArrays: Source[ByteString, Any])

  def props(replicator: ActorRef)(implicit cluster: Cluster): Props =
    Props(new SharedDataActor(replicator))
}

class SharedDataActor(replicator: ActorRef)(implicit cluster: Cluster)
    extends Actor
    with ActorLogging {

  import SharedDataActor._

  var localAddress: InetSocketAddress = _
  var localSubscribers = Map[AccessInfo, Sink[ByteString, NotUsed]]()
  val adminDataKey = ORSetKey[StreamInfo]("streamInfo")
  val accessDataKey = ORSetKey[AccessInfo]("accessInfo")

  replicator ! Subscribe(adminDataKey, self)
  replicator ! Subscribe(accessDataKey, self)

  def receive = {
    case LocalAddress(a) =>
      localAddress = a

    case AddStream(name) =>
      log.info("Adding: {}", name)
      val info = StreamInfo(name)
      replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(
        _ + info)
      sender() ! info

    case DeleteStream(name) =>
      log.info("Removing: {}", name)
      val info = StreamInfo(name)
      replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(
        _ - info)
      sender() ! info

    case ListStreams =>
      replicator ! Get(adminDataKey, ReadLocal, request = Some(sender()))

    case g @ GetSuccess(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      val value = g.get(adminDataKey).elements
      replyTo ! value

    case GetFailure(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case NotFound(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case _: UpdateResponse[_] ⇒ // ignore

    case c @ Changed(`adminDataKey`) ⇒
      val data = c.get(adminDataKey)
      log.info("Current streams: {}", data.elements)

    case AddSubscription(name, sink) =>
      log.info("Adding Subscription: {}", name)
      val info = AccessInfo(name,
                            localAddress.getAddress.getHostAddress,
                            localAddress.getPort,
                            UUID.randomUUID().toString)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(
        _ + info)
      localSubscribers = localSubscribers + (info -> sink)
      sender() ! info

    case DeleteSubscription(info) =>
      log.info("Removing Subscription with id: {}", info)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(
        _ - info)
      sender() ! info

    case ListSubscriptions =>
      replicator ! Get(accessDataKey, ReadLocal, request = Some(sender()))

    case g @ GetSuccess(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      val value = g.get(accessDataKey).elements
      replyTo ! value

    case GetFailure(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case NotFound(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case c @ Changed(`accessDataKey`) ⇒
      val data = c.get(accessDataKey)
      log.info("Current subscriptions: {}", data.elements)

    case Publish(set, byteArrays) =>
//      val localSinks = set.filter(localSubscribers.contains(_))
    // XXX TODO: Broadcase to these sinks
//      localSinks
    // XXX TODO: distribute to remote http servers

  }

  //  override def postStop(): Unit = ???

}
