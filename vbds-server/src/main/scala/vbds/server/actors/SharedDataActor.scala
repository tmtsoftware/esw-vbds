package vbds.server.actors

import java.net.InetSocketAddress
import akka.{Done, NotUsed}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, ReplicatorMessageAdapter}
import akka.cluster.ddata.typed.scaladsl.Replicator.*
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source}
import akka.util.{ByteString, Timeout}
import vbds.server.models.{AccessInfo, ServerInfo, StreamInfo}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse}

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import SharedDataActor.*
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Scheduler, Signal, SpawnProtocol}
import akka.cluster.ddata.{ORSet, ORSetKey, SelfUniqueAddress}
import akka.actor.typed.scaladsl.AskPattern.*
import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent}
import akka.cluster.typed.{Cluster, Leave}
import akka.event.Logging.{DebugLevel, ErrorLevel, InfoLevel, LogLevel, WarningLevel}
import vbds.server.routes.{AccessRoute, AdminRoute, TransferRoute}
import akka.http.scaladsl.server.Directives.*
import csw.logging.client.scaladsl.GenericLoggerFactory
import vbds.server.routes.AccessRoute.WebsocketResponseActorMsg

/**
 * Defines messages handled by the actor
 */
private[server] object SharedDataActor {

  sealed trait SharedDataActorMessages

  // Tell the actor to terminate
  case class StopSharedDataActor(replyTo: ActorRef[Done]) extends SharedDataActorMessages

  case class AddStream(streamName: String, contentType: String, replyTo: ActorRef[StreamInfo]) extends SharedDataActorMessages

  case class DeleteStream(streamName: String, replyTo: ActorRef[StreamInfo]) extends SharedDataActorMessages

  case class ListStreams(replyTo: ActorRef[Set[StreamInfo]]) extends SharedDataActorMessages

  // Tells the actor the host and port that the http server is listening on
  case class LocalAddress(a: InetSocketAddress, binding: Http.ServerBinding) extends SharedDataActorMessages

  case class AddSubscription(
      streamName: String,
      id: String,
      sink: Sink[ByteString, NotUsed],
      wsResponseActor: ActorRef[WebsocketResponseActorMsg],
      replyTo: ActorRef[AccessInfo]
  ) extends SharedDataActorMessages

  case class DeleteSubscription(id: String, replyTo: ActorRef[String]) extends SharedDataActorMessages

  case class ListSubscriptions(replyTo: ActorRef[Set[AccessInfo]]) extends SharedDataActorMessages

  case class Publish(streamName: String, source: Source[ByteString, Any], dist: Boolean, replyTo: ActorRef[Done])
      extends SharedDataActorMessages

  case class LogMessage(logLevel: LogLevel, msg: String) extends SharedDataActorMessages

  // Used by adapters to receive and respond to cluster messages
  // Note: Due to type erasure, it seems that it is not possible to use the full type of ORSet below.
  // It is necessary to match on the key later.
  private sealed trait InternalMsg extends SharedDataActorMessages

  private case class InfoUpdateResponse(rsp: UpdateResponse[ORSet[?]]) extends InternalMsg

  private case class InternalSubscribeResponse(chg: SubscribeResponse[ORSet[?]]) extends InternalMsg

  private case class ReachabilityChange(reachabilityEvent: ReachabilityEvent) extends InternalMsg
  private case class MemberChange(event: MemberEvent)                         extends InternalMsg

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
  private[server] case class LocalSubscriberInfo(
      sink: Sink[ByteString, NotUsed],
      wsResponseActor: ActorRef[WebsocketResponseActorMsg]
  )

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
        case (sub, pub) => (Sink.fromSubscriber(sub), Source.fromPublisher(pub))
      }

  // Key for sharing the list of streams in the cluster
  val adminDataKey: ORSetKey[StreamInfo] = ORSetKey[StreamInfo]("streamInfo")

  // Key for sharing the subscriber information in the cluster
  val accessDataKey = ORSetKey[AccessInfo]("accessInfo")

  // Used to create the actor
  def apply(httpHost: String, httpPort: Int)(
      implicit actorSystem: ActorSystem[SpawnProtocol.Command]
  ): Behavior[SharedDataActorMessages] = {
    Behaviors.setup { ctx =>
      val memberEventAdapter: ActorRef[MemberEvent] = ctx.messageAdapter(MemberChange)
      Cluster(ctx.system).subscriptions ! akka.cluster.typed.Subscribe(memberEventAdapter, classOf[MemberEvent])

      val reachabilityAdapter = ctx.messageAdapter(ReachabilityChange)
      Cluster(ctx.system).subscriptions ! akka.cluster.typed.Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

      DistributedData.withReplicatorMessageAdapter[SharedDataActorMessages, ORSet[?]] { replicatorAdapter =>
        // Subscribe to changes of the given keys
        replicatorAdapter.subscribe(adminDataKey, InternalSubscribeResponse.apply)
        replicatorAdapter.subscribe(accessDataKey, InternalSubscribeResponse.apply)
        new SharedDataActor(ctx, httpHost, httpPort, replicatorAdapter)
      }
    }
  }
}

/**
 * A cluster actor that shares stream and subscription info via CRDT and implements
 * the publish/subscribe features.
 */
//noinspection HttpUrlsUsage
private[server] class SharedDataActor(
    ctx: ActorContext[SharedDataActorMessages],
    httpHost: String,
    httpPort: Int,
    replicatorAdapter: ReplicatorMessageAdapter[SharedDataActorMessages, ORSet[?]]
)(implicit actorSystem: ActorSystem[SpawnProtocol.Command])
    extends AbstractBehavior(ctx) {

  private val log                      = GenericLoggerFactory.getLogger
  implicit val ec: ExecutionContext    = ctx.executionContext
  implicit val scheduler: Scheduler    = actorSystem.scheduler
  implicit val node: SelfUniqueAddress = DistributedData(actorSystem).selfUniqueAddress

  // The local IP address, set at runtime via a message from the code that starts the HTTP server.
  // This is used to determine which HTTP server has the websocket connection to a client.
  var localAddress: InetSocketAddress = _
  var binding: Http.ServerBinding     = _

  // Cache of shared subscription info
  var subscriptions = Set[AccessInfo]()

  // Cache of shared stream info
  var streams = Set[StreamInfo]()

  // A map with information about subscribers that subscribed via this server.
  // The key is from shared cluster data (CRDT) and the value is a Sink that writes to the subscriber's websocket
  var localSubscribers = Map[AccessInfo, LocalSubscriberInfo]()

  // A map with information about remote HTTP servers with subscribers.
  // The key is from shared cluster data (CRDT) and the value is a flow that sends requests to the remote server.
  var remoteConnections = Map[ServerInfo, Flow[HttpRequest, HttpResponse, ?]]()

  // Used to shutdown the HTTP server when done
  startHttpServer()

  override def onMessage(msg: SharedDataActorMessages): Behavior[SharedDataActorMessages] = {
    msg match {
      // Sets the local IP address
      case LocalAddress(a, b) =>
        localAddress = a
        binding = b

      case AddStream(name, contentType, replyTo) =>
        log.debug(s"Adding: $name, $contentType")
        val info = StreamInfo(name, contentType)
        replicatorAdapter.askUpdate(
          askReplyTo =>
            Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal, askReplyTo)(
              _.asInstanceOf[ORSet[StreamInfo]] :+ info
            ),
          InfoUpdateResponse.apply
        )
        replyTo ! info

      case DeleteStream(name, replyTo) =>
        log.debug(s"Removing: $name")
        streams.find(_.name == name).foreach { info =>
          replicatorAdapter.askUpdate(
            askReplyTo =>
              Update(adminDataKey, ORSet.empty[StreamInfo], WriteLocal, askReplyTo)(
                _.asInstanceOf[ORSet[StreamInfo]].remove(info)
              ),
            InfoUpdateResponse.apply
          )
          replyTo ! info
        }

      // --- Sends a request to get the list of streams (See the following cases for the response) ---
      case ListStreams(replyTo) =>
        replyTo ! streams

      case AddSubscription(name, id, sink, wsResponseActor, replyTo) =>
        log.debug(s"Adding Subscription to stream $name with id $id")
        val info = AccessInfo(name, localAddress.getAddress.getHostAddress, localAddress.getPort, id)
        replicatorAdapter.askUpdate(
          askReplyTo =>
            Update(accessDataKey, ORSet.empty[StreamInfo], WriteLocal, askReplyTo)(
              _.asInstanceOf[ORSet[AccessInfo]] :+ info
            ),
          InfoUpdateResponse.apply
        )
        localSubscribers = localSubscribers + (info -> LocalSubscriberInfo(sink, wsResponseActor))
        replyTo ! info

      case DeleteSubscription(id, replyTo) =>
        val s = subscriptions.find(_.id == id)
        s.foreach { info =>
          subscriptions = subscriptions - info
          log.debug(s"Removing Subscription with id: $info")
          replicatorAdapter.askUpdate(
            askReplyTo =>
              Update(accessDataKey, ORSet.empty[StreamInfo], WriteLocal, askReplyTo)(
                _.asInstanceOf[ORSet[AccessInfo]].remove(info)
              ),
            InfoUpdateResponse.apply
          )
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

      case LogMessage(level, msg) =>
        level match {
          case DebugLevel   => log.debug(msg)
          case InfoLevel    => log.debug(msg)
          case WarningLevel => log.warn(msg)
          case ErrorLevel   => log.error(msg)
          case _            =>
        }

      case internal: InternalMsg =>
        internal match {
          case InfoUpdateResponse(_) => Behaviors.same

          case InternalSubscribeResponse(c @ Changed(`adminDataKey`)) =>
            val data = c.get(adminDataKey)
            streams = data.elements
            log.debug(s"Subscribe response: Current streams: $streams")

          case InternalSubscribeResponse(c @ Changed(`accessDataKey`)) =>
            val data = c.get(accessDataKey)
            subscriptions = data.elements
            log.debug(s"Subscribe response: Current subscriptions: $streams")

          case ReachabilityChange(reachabilityEvent) =>
            log.debug(s"ReachabilityChange: $reachabilityEvent")
            // XXX TODO FIXME: Should not be needed!
            Cluster(ctx.system).manager ! Leave(reachabilityEvent.member.address)

          case MemberChange(memberEvent) =>
            log.debug(s"MemberChange: $memberEvent")

          case x => log.error(s"XXX Unexpected internal message: $x")
        }

      case StopSharedDataActor(replyTo) =>
        println("XXX Stopping SharedDataActor")
        val cluster = Cluster(ctx.system)
        cluster.manager ! Leave(cluster.selfMember.address)
        replyTo ! Done
    }

    msg match {
      case StopSharedDataActor(_) =>
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[SharedDataActorMessages]] = {
    case signal if signal == PostStop =>
      println(s"XXX onSignal SharedDataActor Terminating")
      binding.terminate(5.seconds)
      Behaviors.stopped
  }

  // Starts the HTTP server, which is the public API
  private def startHttpServer(): Unit = {
    val adminApi    = new AdminApiImpl(ctx.self)(scheduler, ec)
    val accessApi   = new AccessApiImpl(ctx.self)(scheduler, ec)
    val transferApi = new TransferApiImpl(ctx.self)(scheduler)

    val adminRoute    = new AdminRoute(adminApi)
    val accessRoute   = new AccessRoute(adminApi, accessApi)
    val transferRoute = new TransferRoute(adminApi, transferApi)
    val route         = adminRoute.route ~ accessRoute.route ~ transferRoute.route
    val bindingF      = Http().newServerAt(httpHost, httpPort).bind(route)

    // Need to know this http server's address when subscribing
    bindingF.onComplete {
      case Success(binding) =>
        println(s"VBDS Server running on: http:/${binding.localAddress} (${actorSystem.address})")
        ctx.self ! LocalAddress(new InetSocketAddress(httpHost, binding.localAddress.getPort), binding)
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
  private def publish(
      streamName: String,
      subscriberSet: Set[AccessInfo],
      source: Source[ByteString, Any],
      dist: Boolean
  ): Future[Done] = {

    // Split subscribers into local and remote
    implicit val timeout      = Timeout(20.seconds)
    val (localSet, remoteSet) = getSubscribers(subscriberSet, dist)
    val remoteHostSet         = remoteSet.map(a => ServerInfo(a.host, a.port))
    if (dist) checkRemoteConnections(remoteHostSet)

    // Number of broadcast outputs
    val numOut = localSet.size + remoteHostSet.size
    log.debug(
      s"Publish dist=$dist, subscribers: $numOut (${localSet.size} local, ${remoteSet.size} remote on ${remoteHostSet.size} hosts)"
    )

    // Wait for the client to acknowledge the message
    def waitForAck(a: AccessInfo): Future[Any] = {
      localSubscribers(a).wsResponseActor.ask(AccessRoute.Get)(timeout, scheduler)
    }

    // Send data for a remote subscriber as HTTP POST to the server hosting its websocket
    def remoteFlow(h: ServerInfo): (Future[Done], Flow[ByteString, ByteString, NotUsed]) = {
      val (sink, source) = sinkToSource[ByteString].run()
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
    val g = RunnableGraph.fromGraph(GraphDSL.createGraph(Sink.ignore) { implicit builder => out =>
      import GraphDSL.Implicits.*

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
      case Success(_)  => context.self ! LogMessage(DebugLevel, "Publish complete")
      case Failure(ex) => context.self ! LogMessage(ErrorLevel, s"Publish failed with $ex")
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
        remoteConnections = remoteConnections + (h -> Http()(actorSystem).outgoingConnection(h.host, h.port))
      }
    }
  }
}
