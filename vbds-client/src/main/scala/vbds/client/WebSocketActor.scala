package vbds.client

import java.io.{File, FileOutputStream}
import java.nio.file.Path

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.scaladsl.Sink
import vbds.client.WebSocketActor._

import scala.concurrent.duration._
import scala.util._

object WebSocketActor {

  case object Ack

  case object StreamInitialized

  case object StreamCompleted

  final case class StreamFailure(ex: Throwable)

  final case class ReceivedFile(streamName: String, count: Int, path: Path)

  /**
   * Used to create the actor
   * @param streamName the name of the stream we are subscribed to
   * @param dir the directory in which to save the files received (if saveFiles is true)
   * @param queue a queue to write messages to when a file is received
   * @param saveFiles if true, save the files in the given dir (Set to false for throughput tests)
   */
  def props(streamName: String, dir: File, queue: SourceQueueWithComplete[ReceivedFile], saveFiles: Boolean)(
      implicit system: ActorSystem,
      mat: Materializer
  ): Props =
    Props(new WebSocketActor(streamName, dir, queue, saveFiles))
}

class WebSocketActor(streamName: String, dir: File, queue: SourceQueueWithComplete[ReceivedFile], saveFiles: Boolean)(
    implicit val system: ActorSystem,
    implicit val mat: Materializer
) extends Actor
    with ActorLogging {

  import WebSocketActor._
  import system.dispatcher

  var count                = 0
  var file: File           = _
  var os: FileOutputStream = _
  implicit val askTimeout  = Timeout(5.seconds)

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
          bm.dataStream.mapAsync(1)(bs => (self ? bs).mapTo[Ack.type]).runWith(Sink.ignore).onComplete {
            case Success(_) =>
              replyTo ! Ack // ack to allow the stream to proceed sending more elements
            case Failure(ex) => log.error(ex, "Failed to handle BinaryMessage")
          }

        case x =>
          log.error(s"Wrong message type: $x")
      }

    case bs: ByteString ⇒
      if (bs.size == 1 && bs.utf8String == "\n") {
        if (saveFiles) os.close()
        log.debug(s"Wrote $file")
        queue.offer(ReceivedFile(streamName, count, file.toPath))
        newFile()
      } else {
        if (saveFiles) os.write(bs.toArray)
      }
      sender() ! Ack // ack to allow the stream to proceed sending more elements

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
