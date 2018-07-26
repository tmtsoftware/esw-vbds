package vbds.client

import java.io.{File, FileOutputStream}
import java.nio.file.Path

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.util.{ByteString, Timeout}
import akka.stream.{Materializer, QueueOfferResult}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import vbds.client.WebSocketActor._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util._

/**
 * An actor that receives websocket messages from the VBDS server.
 */
object WebSocketActor {

  sealed trait WebSocketActorMessage

  case object Ack extends WebSocketActorMessage

  case object StreamInitialized extends WebSocketActorMessage

  case object StreamCompleted extends WebSocketActorMessage

  final case class StreamFailure(ex: Throwable) extends WebSocketActorMessage

  final case class ReceivedFile(streamName: String, count: Int, path: Path)

  // Acknowledge message for received websocket message
  val wsAckMessage = TextMessage("ACK")

  /**
   * Used to create the actor
   *
   * @param name the name of the client, for logging
   * @param streamName the name of the stream we are subscribed to
   * @param dir the directory in which to save the files received (if saveFiles is true)
   * @param inQueue a queue to write messages to when a file is received
   * @param outSink a sink to send to server to acknowledge the message, for flow control
   * @param saveFiles if true, save the files in the given dir (Set to false for throughput tests)
   */
  def props(name: String,
            streamName: String,
            dir: File,
            inQueue: SourceQueueWithComplete[ReceivedFile],
            outSink: Sink[Message, NotUsed],
            saveFiles: Boolean)(
      implicit system: ActorSystem,
      mat: Materializer
  ): Props =
    Props(new WebSocketActor(name, streamName, dir, inQueue, outSink, saveFiles))
}

/**
 * An actor that receives websocket messages from the VBDS server.
 */
class WebSocketActor(name: String,
                     streamName: String,
                     dir: File,
                     inQueue: SourceQueueWithComplete[ReceivedFile],
                     outSink: Sink[Message, NotUsed],
                     saveFiles: Boolean)(
    implicit val system: ActorSystem,
    implicit val mat: Materializer
) extends Actor
    with ActorLogging {

  import WebSocketActor._
  import system.dispatcher

  var count                = 0
  var file: File           = _
  var os: FileOutputStream = _
  implicit val askTimeout  = Timeout(6.seconds)

  log.debug(s"$name: Started WebSocketActor")

  // Current server implementation requires an initial ACK from the client
  sendWsAck()

  def receive: Receive = {
    case StreamInitialized ⇒
      log.debug(s"$name: Initialized stream for $streamName")
      count = 0
      newFile()
      sender() ! Ack

    case bm: BinaryMessage ⇒
      val replyTo = sender()
      val f       = bm.dataStream.mapAsync(1)(handleByteString).runWith(Sink.ignore)
      f.onComplete {
        case Success(_) =>
          replyTo ! Ack // ack to allow the stream to proceed sending more elements
          sendWsAck()

        case Failure(ex) =>
          log.error(ex, s"$name: Failed to handle BinaryMessage")
          replyTo ! Ack
          sendWsAck()
      }

    case tm: TextMessage =>
      log.error(s"$name: Wrong message type: $tm")

    case StreamCompleted ⇒
      log.info(s"$name: Stream completed")

    case StreamFailure(ex) ⇒
      log.error(ex, s"$name: Stream failed!")
  }

  private def newFile(): Unit = {
    count = count + 1
    file = new File(dir, s"$streamName-$count")
    if (saveFiles) os = new FileOutputStream(file)
  }

  // Sends a reply on the websocket acknowledging the bytestring, to prevent overflow
  private def sendWsAck(): Unit =
    Source.single(wsAckMessage).runWith(outSink)

  // Called when a ByteString is received on the websocket
  private def handleByteString(bs: ByteString): Future[Unit] = {
    val result = if (bs.size == 1 && bs.utf8String == "\n") {
      if (saveFiles) {
        os.close()
        log.debug(s"$name: Wrote $file")
      }
      if (log.isDebugEnabled) log.debug(s"$name: Queue offer file $count on stream $streamName")
      val rf = ReceivedFile(streamName, count, file.toPath)
      val f  = inQueue.offer(rf)
      f.onComplete {
        case Success(queueOfferResult) =>
          queueOfferResult match {
            case QueueOfferResult.Enqueued =>
              if (log.isDebugEnabled) log.debug(s"$name: Enqueued ${rf.path}")
            case QueueOfferResult.Dropped =>
              log.warning(s"$name: Dropped ${rf.path}")
            case QueueOfferResult.Failure(ex) =>
              log.error(ex, s"$name: Failed to queue ${rf.path}")
            case QueueOfferResult.QueueClosed =>
              log.warning(s"$name: Closed queue on ${rf.path}")
          }
        case Failure(ex) =>
          log.error(ex, s"$name: Failed to enqueue ${rf.path}")
      }
      newFile()
      f.map(_ => ())
    } else {
      if (saveFiles) os.write(bs.toArray)
      Future.successful(())
    }
    result
  }

}
