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
import vbds.server.models.{AccessInfo, StreamInfo}
import akka.pattern.pipe
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpEntity.{Chunked, IndefiniteLength}
import akka.http.scaladsl.model.{ContentTypes, HttpRequest}
import akka.stream.QueueOfferResult.Enqueued

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
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

  case class Publish(subscriberSet: Set[AccessInfo], producer: Source[ByteString, Any], dist: Boolean)

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

    case Publish(subscriberSet, producer, dist) =>
      publish(subscriberSet, producer, sender(), dist)
  }

  /**
   * Publishes the contents of th given data source to the given set of subscribers and send a Done message to
   * the given actor when done.
   *
   * @param subscriberSet set of subscriber info from shared data
   * @param producer      source of the data (reusable via BroadcastHub)
   * @param replyTo       the actor to notify when done
   * @param dist          if true, also distribute the data to the HTTP servers corresponding to any remote subscribers
   */
  private def publish(subscriberSet: Set[AccessInfo],
                      producer: Source[ByteString, Any],
                      replyTo: ActorRef,
                      dist: Boolean): Unit = {

    // Split subscribers into local and remote
    val (localSet, remoteSet) = subscriberSet.partition(localSubscribers.contains _)
    val remoteHostSet         = remoteSet.map(a => AccessInfo(a.streamName, a.host, a.port, ""))
    val numOut                = localSet.size + remoteHostSet.size
    log.info(
      s"Publish dist=$dist, Number of subscribers: ${subscriberSet.size} (${localSet.size} local, ${remoteSet.size} remote on ${remoteHostSet.size} hosts)"
    )


// XXXXXXXXXXXXXXXxx Try broadcast ~> ...

          val g = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => out =>
            import GraphDSL.Implicits._

            val bcast = builder.add(Broadcast[ByteString](numOut))
            val merge = builder.add(Merge[ByteString](numOut))
            val localFlows = localSet.map(a => Flow[ByteString].alsoTo(Sink.foreach(addToQueue(a, _))))
            val remoteFlows = remoteHostSet.map(a => Flow[ByteString].alsoTo(Sink.foreach(bs => localSubscribers(a).offer(bs))))

//            val f1, f2, f4 = Flow[Int].map{i => println(s"YYY $i"); i}
//            val f3 = Flow[Int].map(i => println(s"XXX $i"))

            producer ~> bcast
            localFlows.foreach(bcast ~> _ ~> merge)
            remoteFlows.foreach(bcast ~> _ ~> merge)
            merge ~> Flow[ByteString] ~> out

            ClosedShape
          })

          val x = g.run()
          x.onComplete {
            case Success(_) => println("BroadcastExample1 done")
            case Failure(ex) => ex.printStackTrace()
          }
    // XXXXXXXXXXXXXXXxx Try broadcast ~> ...







//    val f = Future
//      .sequence(Seq(sendToLocalSubscribers(producer, localSet), sendToRemoteSubscribers(producer, remoteSet, dist)))
//      .map(_ => Done)
//
//    f.onComplete {
//      case Success(_)  => log.info("Publish complete")
//      case Failure(ex) => log.error(s"Publish failed with $ex")
//    }
//
//    // Send Done to the replyTo actor when done
//    pipe(f) to replyTo
  }

//  private def sendToLocalSubscribers(producer: Source[ByteString, Any], localSet: Set[AccessInfo]): Future[Done] = {
//    producer.map(bs => localSet.map(addToQueue(_, bs))).runWith(Sink.ignore)
//  }

//  // Only want to transfer images once to a remote server, even if it has multiple local subscribers
//  private def sendToRemoteSubscribers(producer: Source[ByteString, Any],
//                                      remoteSet: Set[AccessInfo],
//                                      dist: Boolean): Future[Done] = {
//    val remoteHostSet         = remoteSet.map(a => AccessInfo(a.streamName, a.host, a.port, ""))
//    if (dist && remoteSet.nonEmpty) {
//      log.info(s"Number of remote hosts with subscribers: ${remoteHostSet.size}")
//      Future.sequence(remoteHostSet.map(a => distribute(a.streamName, a.host, a.port, producer))).map(_ => Done)
//    } else {
//      Future.successful(Done)
//    }
//  }

  private def addToQueue(a: AccessInfo, bs: ByteString): Future[Done] = {
    log.info(s"XXXXXXXXXX bs.size = ${bs.size}")
    val f = localSubscribers(a).offer(bs)
    f.onComplete {
      case Success(Enqueued) => log.info("Enqueued message")
      case Success(result)   => log.error(s"XXX Failed to enqueue message: $result")
      case Failure(ex) =>
        log.error(s"XXX Enqueue exception: $ex")
        self ! DeleteSubscription(a) // XXX TODO FIXME: recover when websocket client goes away!
    }
    f.map(_ => Done)
  }

  /**
   * Distributes the incoming data for the given stream to the HTTP server at the given host and port.
   */

//  private def distribute(streamName: String, host: String, port: Int): Future[Done] = {
//    val uri = s"http://$host:$port$distRoute/$streamName"
//    log.info(s"Distribute data for $streamName to $uri")
//    // , producer: Source[ByteString, Any]
//
//    //    Await.ready(producer.runForeach(bs => log.info(s"ZZZZZZZZZZZZZZZZZZZz bs size = ${bs.size}")), 3.seconds)
//
//    val f = Http().singleRequest(
//      HttpRequest(
//        HttpMethods.POST,
//        uri,
//        entity = HttpEntity(ContentTypes.`application/octet-stream`, producer)
//      )
//    )
//
//    f.map(_ => Done)
//  }


    //  private def distribute(streamName: String, host: String, port: Int, producer: Source[ByteString, Any]): Future[Done] = {
//    val uri = s"http://$host:$port$distRoute/$streamName"
//    log.info(s"Distribute data for $streamName to $uri")
////    Await.ready(producer.runForeach(bs => log.info(s"ZZZZZZZZZZZZZZZZZZZz bs size = ${bs.size}")), 3.seconds)
//
//    val f = Http().singleRequest(
//      HttpRequest(
//        HttpMethods.POST,
//        uri,
//        entity = HttpEntity(ContentTypes.`application/octet-stream`, producer)
//      )
//    )
//
//    f.map(_ => Done)

//    createUploadRequest(streamName, uri, producer).flatMap { request =>
//      val responseFuture = Http().singleRequest(request)
//      responseFuture.onComplete {
//        case Success(response) =>
//          log.info(s"Distributed data to $uri")
////          response.discardEntityBytes()
//
//        case Failure(ex) =>
//          log.error(s"XXX Distributing data to $uri failed with $ex")
//      }
//      responseFuture.map(_ => Done)
//    }
//  }

//  private def createUploadRequest(streamName: String, uri: Uri, producer: Source[ByteString, Any]): Future[HttpRequest] = {
//    val bodyPart = Multipart.FormData.BodyPart("data",
//                                               HttpEntity.IndefiniteLength(ContentTypes.`application/octet-stream`, producer),
//                                               Map("filename" -> streamName))
////    val body = Multipart.FormData(bodyPart)
////    Marshal(body).to[RequestEntity].map { entity =>
////      HttpRequest(method = HttpMethods.POST, uri = uri, entity = entity)
////    }
//
//    Await.ready(producer.runForeach(bs => log.info(s"ZZZZZZZZZZZZZZZZZ bs size = ${bs.size}")), 3.seconds)
//
//    val request = HttpRequest(method = HttpMethods.POST,
//                              uri = uri,
//                              entity = Chunked.fromData(ContentTypes.`application/octet-stream`, producer))
//    Future.successful(request)
//  }

}
