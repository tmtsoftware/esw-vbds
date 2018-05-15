package vbds.server.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{ORSet, ORSetKey}
import akka.cluster.ddata.Replicator._
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.util.ByteString
import vbds.server.models.{AccessInfo, ServerInfo, StreamInfo}
import akka.pattern.pipe
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.{ContentTypes, HttpRequest}

import scala.util.{Failure, Success}

/**
 * Defines messages handled by the actor
 */
private[server] object SharedDataActor {

  sealed trait SharedDataActorMessages

  case class AddStream(streamName: String) extends SharedDataActorMessages

  case class DeleteStream(streamName: String) extends SharedDataActorMessages

  case object ListStreams extends SharedDataActorMessages

  // Tells the actor the host and port that the http server is listening on
  case class LocalAddress(a: InetSocketAddress) extends SharedDataActorMessages

  case class AddSubscription(streamName: String, sink: Sink[ByteString, NotUsed]) extends SharedDataActorMessages

  case class DeleteSubscription(info: AccessInfo) extends SharedDataActorMessages

  case object ListSubscriptions extends SharedDataActorMessages

  case class Publish(streamName: String, subscriberSet: Set[AccessInfo], source: Source[ByteString, Any], dist: Boolean)
      extends SharedDataActorMessages

  // Used to create the actor
  def props(replicator: ActorRef)(implicit cluster: Cluster, mat: ActorMaterializer, system: ActorSystem): Props =
    Props(new SharedDataActor(replicator))

  /**
   * Represents a remote http server that receives data files that it then distributes to its local subscribers
   *
   * @param streamName name of the stream
   * @param host       subscriber's http host
   * @param port       subscriber's http port
   */
  private case class RemoteAccessInfo(streamName: String, host: String, port: Int)

  // Route used to distribute data to remote HTTP server
  val distRoute      = "/vbds/transfer/internal"
  val chunkSize: Int = 1024 * 1024 // XXX TODO FIXME
}

/**
 * A cluster actor that shares stream and subscription info via CRDT and implements
 * the publish/subscribe features.
 */
private[server] class SharedDataActor(replicator: ActorRef)(implicit cluster: Cluster,
                                                            mat: ActorMaterializer,
                                                            system: ActorSystem)
    extends Actor
    with ActorLogging {

  import system._
  import SharedDataActor._

  // The local IP address, set at runtime via a message from the code that starts the HTTP server.
  // This is used to determine which HTTP server has the websocket connection to a client.
  var localAddress: InetSocketAddress = _

  // A map with information about subscribers that subscribed via this server.
  // The key is from shared cluster data (CRDT) and the value is a Sink that writes to the subscriber's websocket
  var localSubscribers = Map[AccessInfo, Sink[ByteString, NotUsed]]()

  // A map with information about remote HTTP servers with subscribers.
  // The key is from shared cluster data (CRDT) and the value is a flow thaht sends requests to the remote server.
  var remoteConnections = Map[ServerInfo, Flow[HttpRequest, HttpResponse, _]]()

  // Key for sharing the list of streams in the cluster
  val adminDataKey = ORSetKey[StreamInfo]("streamInfo")

  // Key for sharing the subscriber information in the cluster
  val accessDataKey = ORSetKey[AccessInfo]("accessInfo")

  // Subscribe to the shared cluster info (CRDT)
  replicator ! Subscribe(adminDataKey, self)
  replicator ! Subscribe(accessDataKey, self)

  def receive = {
    // Sets the local IP address
    case LocalAddress(a) =>
      localAddress = a

    case AddStream(name) =>
      log.debug("Adding: {}", name)
      val info = StreamInfo(name)
      replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(_ + info)
      sender() ! info

    case DeleteStream(name) =>
      log.debug("Removing: {}", name)
      val info = StreamInfo(name)
      replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(_ - info)
      sender() ! info

    // --- Sends a request to get the list of streams (See the following cases for the response) ---
    case ListStreams =>
      replicator ! Get(adminDataKey, ReadLocal, request = Some(sender()))

    // Respond to the ListStreams message with a set of StreamInfo
    case g @ GetSuccess(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      val value = g.get(adminDataKey).elements
      replyTo ! value

    // Failed ListStreams response
    case GetFailure(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    // Response to ListStreams when there are no streams defined
    case NotFound(`adminDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    // --- End of ListStreams responses ---

    case _: UpdateResponse[_] ⇒ // ignore

    case c @ Changed(`adminDataKey`) ⇒
      val data = c.get(adminDataKey)
      log.debug("Current streams: {}", data.elements)

    case AddSubscription(name, sink) =>
      log.debug("Adding Subscription: {}", name)
      val info = AccessInfo(name, localAddress.getAddress.getHostAddress, localAddress.getPort, UUID.randomUUID().toString)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ + info)
      localSubscribers = localSubscribers + (info -> sink)
      sender() ! info

    case DeleteSubscription(info) =>
      log.debug("Removing Subscription with id: {}", info)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ - info)
      sender() ! info

    // --- Sends a request to get the list of subscriptions (See the following cases for the response) ---
    case ListSubscriptions =>
      replicator ! Get(accessDataKey, ReadLocal, request = Some(sender()))

    // Respond to the ListSubscriptions message with a set of AccessInfo
    case g @ GetSuccess(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      val value = g.get(accessDataKey).elements
      replyTo ! value

    // Failed ListSubscriptions response
    case GetFailure(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    // Response to ListSubscriptions when there are no streams defined
    case NotFound(`accessDataKey`, Some(replyTo: ActorRef)) ⇒
      replyTo ! Set.empty

    // --- End of ListSubscriptions responses ---

    case c @ Changed(`accessDataKey`) ⇒
      val data = c.get(accessDataKey)
      log.debug("Current subscriptions: {}", data.elements)

    case Publish(streamName, subscriberSet, producer, dist) =>
      publish(streamName, subscriberSet, producer, sender(), dist)
  }

  /**
   * Publishes the contents of the given data source to the given set of subscribers and sends a Done message to
   * the given actor when done.
   *
   * @param streamName    name of the stream to publish on
   * @param subscriberSet set of subscriber info from shared data for the given stream
   * @param source        source of the data being published
   * @param replyTo       the actor to notify when done
   * @param dist          if true, also distribute the data to the HTTP servers corresponding to any remote subscribers
   */
  private def publish(streamName: String,
                      subscriberSet: Set[AccessInfo],
                      source: Source[ByteString, Any],
                      replyTo: ActorRef,
                      dist: Boolean): Unit = {

    // Split subscribers into local and remote
    val (localSet, remoteSet) = getSubscribers(subscriberSet, dist)
    val remoteHostSet         = remoteSet.map(a => ServerInfo(a.host, a.port))
    if (dist) checkRemoteConnections(remoteHostSet)

    // Number of broadcast outputs
    val numOut = localSet.size + remoteHostSet.size
    log.debug(
      s"Publish dist=$dist, subscribers: $numOut (${localSet.size} local, ${remoteSet.size} remote on ${remoteHostSet.size} hosts)"
    )

    // Construct a runnable graph that broadcasts the published data to all of the subscribers
    val g = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => out =>
      import GraphDSL.Implicits._

      // Broadcast with an output for each subscriber
      val bcast = builder.add(Broadcast[ByteString](numOut))

      // Merge afterwards to get a single output
      val merge = builder.add(Merge[ByteString](numOut))

      // Send data for each local subscribers to its websocket
      def websocketFlow(a: AccessInfo): Flow[ByteString, ByteString, NotUsed] = Flow[ByteString].alsoTo(localSubscribers(a))

      val localFlows = localSet.map(websocketFlow)

      // If dist is true, send data for each remote subscriber as HTTP POST to the server hosting its websocket
      def requestFlow(h: ServerInfo): Flow[ByteString, HttpRequest, NotUsed] = Flow[ByteString].map(makeHttpRequest(streamName, h, _))

      val remoteFlows =
        if (dist) remoteHostSet.map(h => requestFlow(h).via(remoteConnections(h)).map(_ => ByteString.empty)) else Set.empty

      // Create the graph
      source ~> bcast
      localFlows.foreach(bcast ~> _ ~> merge)
      if (dist) remoteFlows.foreach(bcast ~> _ ~> merge)
      merge ~> out
      ClosedShape
    })

    val f = g.run()
    f.onComplete {
      case Success(_)  => log.debug("Publish complete")
      case Failure(ex) => log.error(s"Publish failed with $ex")
    }

    // Send Done to the replyTo actor when done
    pipe(f) to replyTo
  }

  /**
   * Returns a pair of sets for the local and remote subscribers
   *
   * @param subscriberSet all subscribers for the stream
   * @param dist          true if published data should be distributed to remote subscribers
   */
  private def getSubscribers(subscriberSet: Set[AccessInfo], dist: Boolean): (Set[AccessInfo], Set[AccessInfo]) = {
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
}
