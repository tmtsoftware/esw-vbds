package vbds.client

import java.io.{File, FileOutputStream}
import java.nio.file.Path

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import akka.stream.{Materializer, QueueOfferResult}
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.scaladsl.Sink
import vbds.client.WebSocketActor._

import scala.concurrent.duration._
import scala.util._

object WebSocketActor {

  sealed trait WebSocketActorMessage

  case object Ack extends WebSocketActorMessage

  case object StreamInitialized extends WebSocketActorMessage

  case object StreamCompleted extends WebSocketActorMessage

  final case class StreamFailure(ex: Throwable) extends WebSocketActorMessage

  final case class ReceivedFile(streamName: String, count: Int, path: Path)

  /**
   * Used to create the actor
   * @param streamName the name of the stream we are subscribed to
   * @param dir the directory in which to save the files received (if saveFiles is true)
   * @param queue a queue to write messages to when a file is received
   * @param saveFiles if true, save the files in the given dir (Set to false for throughput tests)
   * @param delay optional delay to simulate a slow subscriber
   */
  def props(streamName: String,
            dir: File,
            queue: SourceQueueWithComplete[ReceivedFile],
            saveFiles: Boolean,
            delay: FiniteDuration = Duration.Zero)(
      implicit system: ActorSystem,
      mat: Materializer
  ): Props =
    Props(new WebSocketActor(streamName, dir, queue, saveFiles, delay))
}

class WebSocketActor(streamName: String,
                     dir: File,
                     queue: SourceQueueWithComplete[ReceivedFile],
                     saveFiles: Boolean,
                     delay: FiniteDuration)(
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

  log.debug("Started WebSocketActor")

  def receive: Receive = {
    case StreamInitialized ⇒
      log.debug(s"Initialized stream for $streamName")
      count = 0
      newFile()
      sender() ! Ack

    case msg: Message ⇒
      msg match {
        case bm: BinaryMessage =>
          log.debug(s"Received binary message for stream $streamName")
          val replyTo = sender()
          if (delay != Duration.Zero) Thread.sleep(delay.toMillis) // similate a slow subscriber
          bm.dataStream.mapAsync(1)(bs => (self ? bs).mapTo[Ack.type]).runWith(Sink.ignore).onComplete {
            case Success(_) =>
              replyTo ! Ack // ack to allow the stream to proceed sending more elements
            case Failure(ex) =>
              log.error(ex, s"Failed to handle BinaryMessage")
              replyTo ! Ack
          }

        case x =>
          log.error(s"Wrong message type: $x")
      }

    case bs: ByteString ⇒
      val replyTo = sender()
      if (bs.size == 1 && bs.utf8String == "\n") {
        if (saveFiles) {
          os.close()
          log.debug(s"Wrote $file")
        }
        log.debug(s"Queue offer file $count on stream $streamName")
        queue.offer(ReceivedFile(streamName, count, file.toPath)).onComplete {
          case Success(queueOfferResult) =>
            queueOfferResult match {
              case QueueOfferResult.Enqueued =>
                log.debug(s"Enqueued $file")
              case QueueOfferResult.Dropped =>
                log.warning(s"Dropped $file")
              case QueueOfferResult.Failure(ex) =>
                log.error(ex, s"Failed to queue $file")
              case QueueOfferResult.QueueClosed =>
                log.warning(s"Closed queue on $file")
            }
            replyTo ! Ack
          case Failure(ex) =>
            log.error(ex, s"Failed to enqueue $file")
            replyTo ! Ack
        }
        newFile()
      } else {
        if (saveFiles) os.write(bs.toArray)
        replyTo ! Ack // ack to allow the stream to proceed sending more elements
      }

    case StreamCompleted ⇒
      log.debug("Stream completed")

    case StreamFailure(ex) ⇒
      log.error(ex, "Stream failed!")
  }

  private def newFile(): Unit = {
    count = count + 1
    file = new File(dir, s"$streamName-$count")
    if (saveFiles) os = new FileOutputStream(file)
  }

}
