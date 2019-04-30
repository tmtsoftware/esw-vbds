package vbds.server.actors

import java.net.InetSocketAddress

import akka.{Done, NotUsed}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator._
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source}
import akka.util.{ByteString, Timeout}
import vbds.server.models.{AccessInfo, ServerInfo, StreamInfo}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import SharedDataActor._
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.{Key, ORSet, ORSetKey}
import akka.cluster.typed.Cluster
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.actor.typed.scaladsl.AskPattern._
import vbds.server.routes.WebsocketResponseActor.WebsocketResponseActorMsg
import vbds.server.routes.{AccessRoute, AdminRoute, TransferRoute, WebsocketResponseActor}
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.server.Directives._

/**
 * Defines messages handled by the actor
 */
private[server] object SharedDataActor {

  sealed trait SharedDataActorMessages

  case class AddStream(streamName: String, contentType: String, replyTo: ActorRef[StreamInfo]) extends SharedDataActorMessages

  case class DeleteStream(streamName: String, replyTo: ActorRef[StreamInfo]) extends SharedDataActorMessages

  case class ListStreams(replyTo: ActorRef[Set[StreamInfo]]) extends SharedDataActorMessages

  // Tells the actor the host and port that the http server is listening on
  case class LocalAddress(a: InetSocketAddress) extends SharedDataActorMessages

  case class AddSubscription(streamName: String,
                             id: String,
                             sink: Sink[ByteString, NotUsed],
                             wsResponseActor: ActorRef[WebsocketResponseActorMsg],
                             replyTo: ActorRef[AccessInfo])
      extends SharedDataActorMessages

  case class DeleteSubscription(id: String, replyTo: ActorRef[String]) extends SharedDataActorMessages

  case class ListSubscriptions(replyTo: ActorRef[Set[AccessInfo]]) extends SharedDataActorMessages

  case class Publish(streamName: String, source: Source[ByteString, Any], dist: Boolean, replyTo: ActorRef[Done])
      extends SharedDataActorMessages

  // Used by adapters to receive and respond to cluster messages
  private sealed trait InternalMsg extends SharedDataActorMessages

  private case class StreamInfoChanged(chg: Changed[ORSet[StreamInfo]]) extends InternalMsg

  private case class AccessInfoChanged(chg: Changed[ORSet[AccessInfo]]) extends InternalMsg

  private case class StreamInfoUpdateResponse(rsp: UpdateResponse[ORSet[StreamInfo]]) extends InternalMsg

  private case class AccessInfoUpdateResponse(rsp: UpdateResponse[ORSet[AccessInfo]]) extends InternalMsg

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
                                                 wsResponseActor: ActorRef[WebsocketResponseActorMsg])

  // Route used to distribute data to remote HTTP server
  val distRoute = "/vbds/transfer/internal"

  /**
   * Materializes into a sink connected to a source, i.e. the sink pushes into the source:
   *
   * +----------+       +----------+
   * >   Sink   |------>|  Source  >
   * +----------+       +----------+
   *
   * Should be provided by Akka Streams, see https://github.com/akka/akka/issues/24853.
   */
  def sinkToSource[M]: RunnableGraph[(Sink[M, NotUsed], Source[M, NotUsed])] =
    Source
      .asSubscriber[M]
      .toMat(Sink.asPublisher[M](fanout = false))(Keep.both)
      .mapMaterializedValue {
        case (sub, pub) ⇒ (Sink.fromSubscriber(sub), Source.fromPublisher(pub))
      }

  // Used to create the actor
  def apply(httpHost: String, httpPort: Int): Behavior[SharedDataActorMessages] = {
    Behaviors.setup(ctx => new SharedDataActor(ctx, httpHost, httpPort))
  }
}

/**
 * A cluster actor that shares stream and subscription info via CRDT and implements
 * the publish/subscribe features.
 */
private[server] class SharedDataActor(ctx: ActorContext[SharedDataActorMessages], httpHost: String, httpPort: Int)
    extends AbstractBehavior[SharedDataActorMessages] {

  import ctx.log
  implicit val ec: ExecutionContext = ctx.executionContext
  implicit val scheduler            = ctx.system.scheduler
  implicit val node = DistributedData(ctx.system).selfUniqueAddress
  implicit val mat  = ActorMaterializer()(ctx.system)

  val replicator = DistributedData(ctx.system).replicator
  val cluster    = Cluster(ctx.system)

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
  // The key is from shared cluster data (CRDT) and the value is a flow that sends requests to the remote server.
  var remoteConnections = Map[ServerInfo, Flow[HttpRequest, HttpResponse, _]]()

  // Key for sharing the list of streams in the cluster
  val adminDataKey: ORSetKey[StreamInfo] = ORSetKey[StreamInfo]("streamInfo")

  // Key for sharing the subscriber information in the cluster
  val accessDataKey = ORSetKey[AccessInfo]("accessInfo")

  // Adapters used so that typed actor can receive and respond to cluster messages
  val streamInfoChangedAdapter = ctx.messageAdapter(StreamInfoChanged.apply)
  val accessInfoChangedAdapter = ctx.messageAdapter(AccessInfoChanged.apply)
  val streamInfoUpdateResponseAdapter = ctx.messageAdapter(StreamInfoUpdateResponse.apply)
  val accessInfoUpdateResponseAdapter = ctx.messageAdapter(AccessInfoUpdateResponse.apply)

  startHttpServer()

  // Subscribe to the shared cluster info (CRDT)
  replicator ! Subscribe(adminDataKey, streamInfoChangedAdapter)
  replicator ! Subscribe(accessDataKey, accessInfoChangedAdapter)

  override def onMessage(msg: SharedDataActorMessages): Behavior[SharedDataActorMessages] = {
    msg match {
      // Sets the local IP address
      case LocalAddress(a) =>
        localAddress = a

      case AddStream(name, contentType, replyTo) =>
        log.info("Adding: {}: {}", name, contentType)
        val info = StreamInfo(name, contentType)
        replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal, streamInfoUpdateResponseAdapter)(_ :+ info)

//        replicator ! new Update(adminDataKey, WriteLocal, streamInfoUpdateResponseAdapter, None)(
//          _.getOrElse(ORSet.empty[StreamInfo] :+ info)
//        )
        replyTo ! info

      case DeleteStream(name, replyTo) =>
        log.info("Removing: {}", name)
        streams.find(_.name == name).foreach { info =>
          replicator ! Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal, streamInfoUpdateResponseAdapter)(_.remove(info))
//          replicator ! new Update(adminDataKey, WriteLocal, streamInfoUpdateResponseAdapter, None)(
//            _.getOrElse(ORSet.empty[StreamInfo].remove(info))
//          )
          replyTo ! info
        }

      // --- Sends a request to get the list of streams (See the following cases for the response) ---
      case ListStreams(replyTo) =>
        replyTo ! streams

      case AddSubscription(name, id, sink, wsResponseActor, replyTo) =>
        log.info(s"Adding Subscription to stream $name with id $id")
        val info = AccessInfo(name, localAddress.getAddress.getHostAddress, localAddress.getPort, id)
        replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal, accessInfoUpdateResponseAdapter)(_ :+ info)
        //        replicator ! new Update(accessDataKey, WriteLocal, accessInfoUpdateResponseAdapter, None)(
//          _.getOrElse(ORSet.empty[AccessInfo] :+ info)
//        )
        localSubscribers = localSubscribers + (info -> LocalSubscriberInfo(sink, wsResponseActor))
        replyTo ! info

      case DeleteSubscription(id, replyTo) =>
        val s = subscriptions.find(_.id == id)
        s.foreach { info =>
          log.info("Removing Subscription with id: {}", info)
          replicator ! Update(accessDataKey, ORSet.empty[AccessInfo], WriteLocal, accessInfoUpdateResponseAdapter)(_.remove(info))
          //          replicator ! new Update(accessDataKey, WriteLocal, accessInfoUpdateResponseAdapter, None)(
//            _.getOrElse(ORSet.empty[AccessInfo].remove(info))
//          )
        }
        replyTo ! id

      // --- Sends a request to get the list of subscriptions (See the following cases for the response) ---
      case ListSubscriptions(replyTo) =>
        replyTo ! subscriptions

      case Publish(streamName, source, dist, replyTo) =>
        val subscriberSet = subscriptions.filter(_.streamName == streamName)
        val f = if (subscriberSet.isEmpty) {
          // No subscribers, so just ignore
          source.runWith(Sink.ignore)
        } else {
          // Publish to subscribers and
          publish(streamName, subscriberSet, source, dist)
        }
        // reply with Done when done
        f.foreach(replyTo ! _)

      case internal: InternalMsg =>
        internal match {
          case StreamInfoUpdateResponse(_) => Behaviors.same
          case AccessInfoUpdateResponse(_) => Behaviors.same

          case StreamInfoChanged(c@Changed(`adminDataKey`)) =>
            val data = c.get(adminDataKey)
            streams = data.elements
            log.info("Current streams: {}", streams)

          case AccessInfoChanged(c@Changed(`accessDataKey`)) =>
            val data = c.get(accessDataKey)
            subscriptions = data.elements
            log.info("Current subscriptions: {}", subscriptions)
        }
    }
    Behaviors.same
  }

  // Starts the HTTP server, which is the public API
  private def startHttpServer(): Unit = {
    val adminApi    = new AdminApiImpl(ctx.self)
    val accessApi   = new AccessApiImpl(ctx.self)
    val transferApi = new TransferApiImpl(ctx.self, accessApi)

    val adminRoute    = new AdminRoute(adminApi)
    val accessRoute   = new AccessRoute(adminApi, accessApi, ctx)
    val transferRoute = new TransferRoute(adminApi, accessApi, transferApi, ctx)
    val route         = adminRoute.route ~ accessRoute.route ~ transferRoute.route
    implicit val untypedSystem = ctx.system.toUntyped
    val bindingF      = Http().bindAndHandle(route, httpHost, httpPort)
    // Need to know this http server's address when subscribing
    bindingF.onComplete {
      case Success(binding) =>
        println(s"HTTP Server running on: http:/${binding.localAddress}")
        ctx.self ! LocalAddress(new InetSocketAddress(httpHost, binding.localAddress.getPort))
      case Failure(error) =>
        println(error)
        System.exit(1)
    }
  }

  /**
   * Publishes the contents of the given data source to the given set of subscribers and returns a future that completes when
   * done.
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
    implicit val timeout      = Timeout(20.seconds)
    val (localSet, remoteSet) = getSubscribers(subscriberSet, dist)
    val remoteHostSet         = remoteSet.map(a => ServerInfo(a.host, a.port))
    if (dist) checkRemoteConnections(remoteHostSet)

    // Number of broadcast outputs
    val numOut = localSet.size + remoteHostSet.size
    log.info(
      s"Publish dist=$dist, subscribers: $numOut (${localSet.size} local, ${remoteSet.size} remote on ${remoteHostSet.size} hosts)"
    )

    // Wait for the client to acknowledge the message
    def waitForAck(a: AccessInfo): Future[Any] = {
      localSubscribers(a).wsResponseActor.ask(WebsocketResponseActor.Get)
    }

    // Send data for a remote subscriber as HTTP POST to the server hosting its websocket
    def remoteFlow(h: ServerInfo): (Future[Done], Flow[ByteString, ByteString, NotUsed]) = {
      val (sink, source) = sinkToSource[ByteString].run
      val f = Source
        .single(makeHttpRequest(streamName, h, source))
        .via(remoteConnections(h))
        .runWith(Sink.ignore)

      (f, Flow[ByteString].alsoTo(sink))
    }

    // Send data for each local subscribers to its websocket.
    // Wait for ws client to respond in order to avoid overflowing the ws input buffer.
    def websocketFlow(a: AccessInfo) =
      Flow[ByteString]
        .alsoTo(localSubscribers(a).sink)

    // Set of flows to local subscriber websockets
    val localFlows = localSet.map(websocketFlow)

    // Set of flows to remote servers containing subscribers
    val remoteFlowPairs = if (dist) remoteHostSet.map(remoteFlow) else Set.empty
    val remoteFlows     = remoteFlowPairs.map(_._2)
    val remoteResponses = remoteFlowPairs.map(_._1)

    // Construct a runnable graph that broadcasts the published data to all of the subscribers
    val g = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => out =>
      import GraphDSL.Implicits._

      // Broadcast with an output for each subscriber
      val bcast = builder.add(Broadcast[ByteString](numOut))

      // Merge afterwards to get a single output
      val merge = builder.add(Merge[ByteString](numOut))

      // Create the graph
      source ~> bcast
      localFlows.foreach(bcast ~> _ ~> merge)
      if (dist) remoteFlows.foreach(bcast ~> _ ~> merge)
      merge ~> out
      ClosedShape
    })

    val localResponses = localSet.map(waitForAck)
    val f              = Future.sequence(g.run() :: (localResponses ++ remoteResponses).toList).map(_ => Done)
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
  private def makeHttpRequest(streamName: String, serverInfo: ServerInfo, source: Source[ByteString, Any]): HttpRequest = {
    HttpRequest(
      HttpMethods.POST,
      s"http://${serverInfo.host}:${serverInfo.port}$distRoute/$streamName",
      entity = HttpEntity(ContentTypes.`application/octet-stream`, source)
    )
  }

  /**
   * Make sure the remote connections for any remote hosts with subscribers is defined.
   * (XXX TODO: Handle connection timeouts?)
   */
  private def checkRemoteConnections(remoteHostSet: Set[ServerInfo]): Unit = {
    remoteHostSet.foreach { h =>
      if (!remoteConnections.contains(h)) {
        remoteConnections = remoteConnections + (h -> Http()(ctx.system.toUntyped).outgoingConnection(h.host, h.port))
      }
    }
  }
}
