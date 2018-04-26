package vbds.server.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{ORSet, ORSetKey}
import akka.cluster.ddata.Replicator._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import akka.util.ByteString
import vbds.server.models.{AccessInfo, StreamInfo}
import akka.pattern.pipe

import scala.concurrent.Future

object SharedDataActor {

  case class AddStream(streamName: String)

  case class DeleteStream(streamName: String)

  case object ListStreams

  // Sets the host and port that the http server is listening on
  case class LocalAddress(a: InetSocketAddress)

  case class AddSubscription(streamName: String, queue: SourceQueueWithComplete[ByteString])

  case class DeleteSubscription(info: AccessInfo)

  case object ListSubscriptions

  case class Publish(subscriberSet: Set[AccessInfo], byteStrings: Source[ByteString, Any])

  def props(replicator: ActorRef)(implicit cluster: Cluster, mat: ActorMaterializer, system: ActorSystem): Props =
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

// XXX TODO: Split into separate actors
class SharedDataActor(replicator: ActorRef)(implicit cluster: Cluster, mat: ActorMaterializer, system: ActorSystem)
  extends Actor with ActorLogging {

  import system._
  import SharedDataActor._

  var localAddress: InetSocketAddress = _
  var localSubscribers = Map[AccessInfo, SourceQueueWithComplete[ByteString]]()
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
      replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(_ + info)
      sender() ! info

    case DeleteStream(name) =>
      log.info("Removing: {}", name)
      val info = StreamInfo(name)
      replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(_ - info)
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

    case AddSubscription(name, queue) =>
      log.info("Adding Subscription: {}", name)
      val info = AccessInfo(name,
        localAddress.getAddress.getHostAddress,
        localAddress.getPort,
        UUID.randomUUID().toString)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ + info)
      localSubscribers = localSubscribers + (info -> queue)
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
      publish(subscriberSet, byteArrays, sender())
  }

  private def publish(subscriberSet: Set[AccessInfo],
                      byteStrings: Source[ByteString, Any],
                      replyTo: ActorRef
                     ): Unit = {
    log.info(s"Number of subscribers: ${subscriberSet.size}")

    val (localSet, remoteSet) = subscriberSet.partition(localSubscribers.contains _)

    // Write the published data to each local subscriber's queue
    // XXX TODO FIXME: Fan out if multiple subscribers!
    val f = if (localSet.nonEmpty) {
      val result = localSet.map(localSubscribers).map { queue =>
        log.info(s"XXX publish to local queue")
        byteStrings.runForeach(queue.offer)
      }
      Future.sequence(result).map(_ => Done)
    } else {
      Future.successful(Done)
    }

    //    remoteSet.foreach { accessInfo =>
    //      log.info(s"XXX publish to remote server: $accessInfo")
    //    }

    // XXX TODO: Merge local f and remote results in reply!
    pipe(f) to replyTo
  }

}
