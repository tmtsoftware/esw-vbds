package vbds.server.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{ORSet, ORSetKey}
import akka.cluster.ddata.Replicator._
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString
import vbds.server.models.{AccessInfo, ServerInfo, StreamInfo}
import akka.pattern.pipe
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.{ContentTypes, HttpRequest}
import akka.stream.QueueOfferResult.Enqueued

import scala.concurrent.Future
import scala.util.{Failure, Success}

object SharedDataActor {

  case class AddStream(streamName: String)

  case class DeleteStream(streamName: String)

  case object ListStreams

  // Sets the host and port that the http server is listening on
  case class LocalAddress(a: InetSocketAddress)

  case class AddSubscription(streamName: String, queue: SourceQueueWithComplete[ByteString])

  case class DeleteSubscription(info: AccessInfo)

  case object ListSubscriptions

  case class Publish(streamName: String, subscriberSet: Set[AccessInfo], producer: Source[ByteString, Any], dist: Boolean)

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

  // Route used to distribute image to remote HTTP server
  val distRoute      = "/vbds/transfer/internal"
  val chunkSize: Int = 1024 * 1024 // XXX TODO FIXME
}

// XXX TODO: Split into separate actors
class SharedDataActor(replicator: ActorRef)(implicit cluster: Cluster, mat: ActorMaterializer, system: ActorSystem)
    extends Actor
    with ActorLogging {

  import system._
  import SharedDataActor._

  var localAddress: InetSocketAddress = _
  var localSubscribers                = Map[AccessInfo, SourceQueueWithComplete[ByteString]]()
  var remoteConnections               = Map[ServerInfo, Flow[HttpRequest, HttpResponse, _]]()
  val adminDataKey                    = ORSetKey[StreamInfo]("streamInfo")
  val accessDataKey                   = ORSetKey[AccessInfo]("accessInfo")

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

    case AddSubscription(name, queue) =>
      log.info("Adding Subscription: {}", name)
      val info = AccessInfo(name, localAddress.getAddress.getHostAddress, localAddress.getPort, UUID.randomUUID().toString)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ + info)
      localSubscribers = localSubscribers + (info -> queue)
      sender() ! info

    case DeleteSubscription(info) =>
      log.info("Removing Subscription with id: {}", info)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ - info)
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

    case Publish(streamName, subscriberSet, producer, dist) =>
      publish(streamName, subscriberSet, producer, sender(), dist)
  }

  /**
   * Publishes the contents of th given data source to the given set of subscribers and send a Done message to
   * the given actor when done.
   *
   * @param streamName name of the stream to publish on
   * @param subscriberSet set of subscriber info from shared data for the given stream
   * @param producer      source of the data (reusable via BroadcastHub)
   * @param replyTo       the actor to notify when done
   * @param dist          if true, also distribute the data to the HTTP servers corresponding to any remote subscribers
   */
  private def publish(streamName: String,
                      subscriberSet: Set[AccessInfo],
                      producer: Source[ByteString, Any],
                      replyTo: ActorRef,
                      dist: Boolean): Unit = {

    // Split subscribers into local and remote
    val (localSet, remoteSet) = getSubscribers(subscriberSet, dist)
    val remoteHostSet         = remoteSet.map(a => ServerInfo(a.host, a.port))
    if (dist) checkRemoteConnections(remoteHostSet)

    val numOut = localSet.size + remoteHostSet.size
    log.info(
      s"Publish dist=$dist, subscribers: $numOut (${localSet.size} local, ${remoteSet.size} remote on ${remoteHostSet.size} hosts)"
    )

    val g = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => out =>
      import GraphDSL.Implicits._

      val bcast                      = builder.add(Broadcast[ByteString](numOut))
      val merge                      = builder.add(Merge[ByteString](numOut))
      def queueFlow(a: AccessInfo)   = Flow[ByteString].alsoTo(Sink.foreach(addToQueue(a, _)))
      val localFlows                 = localSet.map(queueFlow)
      def requestFlow(h: ServerInfo) = Flow[ByteString].map(makeHttpRequest(streamName, h, _))
      val remoteFlows =
        if (dist) remoteHostSet.map(h => requestFlow(h).via(remoteConnections(h)).map(_ => ByteString.empty)) else Set.empty

      producer ~> bcast
      localFlows.foreach(bcast ~> _ ~> merge)
      if (dist) remoteFlows.foreach(bcast ~> _ ~> merge)
      merge ~> out
      ClosedShape
    })

    val f = g.run()
    f.onComplete {
      case Success(_)  => log.info("Publish complete")
      case Failure(ex) => log.error(s"Publish failed with $ex")
    }

    // Send Done to the replyTo actor when done
    pipe(f) to replyTo

  }

  /**
    * Returns a pair of sets for the local and remote subscribers
    * @param subscriberSet all subscribers for the stream
    * @param dist true if published data should be distributed to remote subscribers
    */
  private def getSubscribers(subscriberSet: Set[AccessInfo], dist: Boolean) = {
    val (localSet, remoteSet) = subscriberSet.partition(localSubscribers.contains _)
    if (dist) (localSet, remoteSet) else (localSet, Set.empty[AccessInfo])
  }

  /**
    * Makes the HTTP request for the transfer to remote servers with subscribers
    */
  private def makeHttpRequest(streamName: String, serverInfo: ServerInfo, bs: ByteString): HttpRequest = {
    HttpRequest(
      HttpMethods.POST,
      s"http://${serverInfo.host}:${serverInfo.port}$distRoute/$streamName",
      entity = HttpEntity(ContentTypes.`application/octet-stream`, bs)
    )
  }

  /**
    * Make sure the remote connections for any remote hosts with subscribers is defined.
    * (XXX TODO: Handle connection timeouts!!!)
    */
  private def checkRemoteConnections(remoteHostSet: Set[ServerInfo]): Unit = {
    remoteHostSet.foreach { h =>
      if (!remoteConnections.contains(h)) {
        remoteConnections = remoteConnections + (h -> Http().outgoingConnection(h.host, h.port))
      }
    }
  }

  /**
    * Adds a ByteString to the queue for a local subscriber's web socket
    */
  private def addToQueue(a: AccessInfo, bs: ByteString): Future[Done] = {
    val f = localSubscribers(a).offer(bs)
    f.onComplete {
      case Success(Enqueued) => log.info("Enqueued message")
      case Success(result)   => log.error(s"Failed to enqueue message: $result")
      case Failure(ex) =>
        log.error(s"Enqueue exception: $ex")
        self ! DeleteSubscription(a) // XXX TODO FIXME: recover when websocket client goes away!
    }
    f.map(_ => Done)
  }
}
