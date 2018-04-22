package vbds.server.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{ORSet, ORSetKey}
import akka.cluster.ddata.Replicator._
import akka.stream.ActorMaterializer
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

  case class Publish(subscriberSet: Set[AccessInfo], byteArrays: Source[ByteString, Any])

  def props(replicator: ActorRef)(implicit cluster: Cluster, mat: ActorMaterializer): Props =
    Props(new SharedDataActor(replicator))

  /**
    * Represents a remote http server that receives images that it then distributes to its local subscribers
    *
    * @param streamName name of the stream
    * @param host       subscriber's http host
    * @param port       subscriber's http port
    */
  private case class RemoteAccessInfo(streamName: String, host: String, port: Int)
}

// XXX TODO: Split into separate actors?lkjpoi098
class SharedDataActor(replicator: ActorRef)(implicit cluster: Cluster, mat: ActorMaterializer)
  extends Actor with ActorLogging {

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

    case g@GetSuccess(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      val value = g.get(adminDataKey).elements
      replyTo ! value

    case GetFailure(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case NotFound(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case _: UpdateResponse[_] ⇒ // ignore

    case c@Changed(`adminDataKey`) ⇒
      val data = c.get(adminDataKey)
      log.info("Current streams: {}", data.elements)

    case AddSubscription(name, sink) =>
      log.info("Adding Subscription: {}", name)
      val info = AccessInfo(name,
        localAddress.getAddress.getHostAddress,
        localAddress.getPort,
        UUID.randomUUID().toString)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ + info)
      localSubscribers = localSubscribers + (info -> sink)
      sender() ! info

    case DeleteSubscription(info) =>
      log.info("Removing Subscription with id: {}", info)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ - info)
      sender() ! info

    case ListSubscriptions =>
      replicator ! Get(accessDataKey, ReadLocal, request = Some(sender()))

    case g@GetSuccess(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      val value = g.get(accessDataKey).elements
      replyTo ! value

    case GetFailure(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case NotFound(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    case c@Changed(`accessDataKey`) ⇒
      val data = c.get(accessDataKey)
      log.info("Current subscriptions: {}", data.elements)

    case Publish(subscriberSet, byteArrays) =>
      publish(subscriberSet, byteArrays)

  }

  private def publish(subscriberSet: Set[AccessInfo], byteArrays: Source[ByteString, Any]): Unit = {
    val f = localSubscribers.contains _
    subscriberSet.filter(f(_)).map(localSubscribers).foreach { sink =>
      println(s"XXX publish to local sink")
      byteArrays.runWith(sink) // XXX TODO: Run in parallel, but wait for all to complete?
    }

    val remoteServers = subscriberSet.filter(!f(_)).map(a => RemoteAccessInfo(a.streamName, a.host, a.port))
    // XXX TODO: distribute to remote http servers (Add new route for distributing image between servers)
    remoteServers.foreach { a =>
      println(s"XXX remote server $a")
    }

  }

  //  override def postStop(): Unit = ???

}
