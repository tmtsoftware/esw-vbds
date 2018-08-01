package vbds.server.actors

import java.net.InetSocketAddress

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.{ORSet, ORSetKey}
import akka.cluster.ddata.Replicator._
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.util.{ByteString, Timeout}
import vbds.server.models.{AccessInfo, ServerInfo, StreamInfo}
import akka.pattern.pipe
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.{ContentTypes, HttpRequest}
import akka.pattern.ask

import scala.concurrent.duration._
import vbds.server.routes.AccessRoute.WebsocketResponseActor

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Defines messages handled by the actor
 */
private[server] object SharedDataActor {

  sealed trait SharedDataActorMessages

  case class AddStream(streamName: String, contentType: String) extends SharedDataActorMessages

  case class DeleteStream(streamName: String) extends SharedDataActorMessages

  case object ListStreams extends SharedDataActorMessages

  // Tells the actor the host and port that the http server is listening on
  case class LocalAddress(a: InetSocketAddress) extends SharedDataActorMessages

  case class AddSubscription(streamName: String,
                             id: String,
                             sink: Sink[ByteString, NotUsed],
                             wsResponseActor: ActorRef)
      extends SharedDataActorMessages

  case class DeleteSubscription(id: String) extends SharedDataActorMessages

  case object ListSubscriptions extends SharedDataActorMessages

  case class Publish(streamName: String, source: Source[ByteString, Any], dist: Boolean) extends SharedDataActorMessages

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

  /**
   * Holds information per local subscriber
   *
   * @param sink            Sink that writes to the subscriber's websocket
   * @param wsResponseActor an Actor that is used to check if the client has processed the last message (to avoid overflow)
   */
  private[server] case class LocalSubscriberInfo(sink: Sink[ByteString, NotUsed],
                                                 wsResponseActor: ActorRef)

  // Route used to distribute data to remote HTTP server
  val distRoute = "/vbds/transfer/internal"
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

  import system.dispatcher
  import SharedDataActor._

  // The local IP address, set at runtime via a message from the code that starts the HTTP server.
  // This is used to determine which HTTP server has the websocket connection to a client.
  var localAddress: InetSocketAddress = _

  // Cache of shared subscription info
  var subscriptions = Set[AccessInfo]()

  // Cache of shared stream info
  var streams = Set[StreamInfo]()

  // A map with information about subscribers that subscribed via this server.
  // The key is from shared cluster data (CRDT) and the value is a Sink that writes to the subscriber's websocket
  var localSubscribers = Map[AccessInfo, LocalSubscriberInfo]()

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

    case AddStream(name, contentType) =>
      log.info("Adding: {}: {}", name, contentType)
      val info = StreamInfo(name, contentType)
      replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(_ + info)
      sender() ! info

    case DeleteStream(name) =>
      log.info("Removing: {}", name)
      streams.find(_.name == name).foreach { info =>
        replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal)(_ - info)
        sender() ! info
      }

    // --- Sends a request to get the list of streams (See the following cases for the response) ---
    case ListStreams =>
      sender ! streams

    case _: UpdateResponse[_] ⇒ // ignore

    case c @ Changed(`adminDataKey`) ⇒
      val data = c.get(adminDataKey)
      streams = data.elements
      log.info("Current streams: {}", streams)

    case AddSubscription(name, id, sink, wsResponseActor) =>
      log.info(s"Adding Subscription to stream $name with id $id")
      val info = AccessInfo(name, localAddress.getAddress.getHostAddress, localAddress.getPort, id)
      replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ + info)
      localSubscribers = localSubscribers + (info -> LocalSubscriberInfo(sink, wsResponseActor))
      sender() ! info

    case DeleteSubscription(id) =>
      val s = subscriptions.find(_.id == id)
      s.foreach { info =>
        log.info("Removing Subscription with id: {}", info)
        replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal)(_ - info)
      }
      sender() ! id

    // --- Sends a request to get the list of subscriptions (See the following cases for the response) ---
    case ListSubscriptions =>
      sender() ! subscriptions

    case c @ Changed(`accessDataKey`) ⇒
      val data = c.get(accessDataKey)
      subscriptions = data.elements
      log.info("Current subscriptions: {}", subscriptions)

    case Publish(streamName, source, dist) =>
      val subscriberSet = subscriptions.filter(_.streamName == streamName)
      val f = if (subscriberSet.isEmpty) {
        // No subscribers, so just ignore
        source.runWith(Sink.ignore)
      } else {
        // Publish to subscribers and
        publish(streamName, subscriberSet, source, dist)
      }
      // reply with Done when done
      pipe(f) to sender()

  }

  /**
   * Publishes the contents of the given data source to the given set of subscribers and sends a Done message to
   * the given actor when done.
   *
   * @param streamName    name of the stream to publish on
   * @param subscriberSet the set of subscribers for the stream
   * @param source        source of the data being published
   * @param dist          if true, also distribute the data to the HTTP servers corresponding to any remote subscribers
   */
  private def publish(streamName: String,
                      subscriberSet: Set[AccessInfo],
                      source: Source[ByteString, Any],
                      dist: Boolean): Future[Done] = {

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
      implicit val timeout = Timeout(20.seconds)

      // Broadcast with an output for each subscriber
      val bcast = builder.add(Broadcast[ByteString](numOut))

      // Merge afterwards to get a single output
      val merge = builder.add(Merge[ByteString](numOut))

      // Wait for the client to acknowledge the message
      def waitForAck(a: AccessInfo): Future[Any] = {
        localSubscribers(a).wsResponseActor ? WebsocketResponseActor.Get
      }

      // Send data for each local subscribers to its websocket.
      // Wait for ws client to respond in order to avoid overflowing the ws input buffer.
      def websocketFlow(a: AccessInfo) =
        Flow[ByteString]
          .alsoTo(localSubscribers(a).sink)
          .mapAsync(1) { _ =>
            waitForAck(a).map(_ => ByteString.empty)
          }

      // Set of flows to local subscriber websockets
      val localFlows = localSet.map(websocketFlow)

      // If dist is true, send data for each remote subscriber as HTTP POST to the server hosting its websocket
      def remoteFlow(h: ServerInfo) =
        Flow[ByteString]
          .map(makeHttpRequest(streamName, h, _))
          .via(remoteConnections(h))
          .map(_ => ByteString.empty)

      // Set of flows to remote servers containing subscribers
      val remoteFlows = if (dist) remoteHostSet.map(remoteFlow) else Set.empty

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
    f
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
   * (XXX TODO: Handle connection timeouts?)
   */
  private def checkRemoteConnections(remoteHostSet: Set[ServerInfo]): Unit = {
    remoteHostSet.foreach { h =>
      if (!remoteConnections.contains(h)) {
        remoteConnections = remoteConnections + (h -> Http().outgoingConnection(h.host, h.port))
      }
    }
  }
}
