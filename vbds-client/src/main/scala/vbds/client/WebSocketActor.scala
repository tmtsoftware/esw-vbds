package vbds.client

import java.io.{File, FileOutputStream}
import java.nio.file.Path

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.util.{ByteString, Timeout}
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util._
import akka.pattern.ask

/**
 * An actor that receives websocket messages from the VBDS server.
 */
object WebSocketActor {

  sealed trait WebSocketActorMessage

  case object Ack extends WebSocketActorMessage

  case object StreamInitialized extends WebSocketActorMessage

  case object StreamCompleted extends WebSocketActorMessage

  final case class StreamFailure(ex: Throwable) extends WebSocketActorMessage

  final case class HandleByteString(bs: ByteString)

  final case class ReceivedFile(streamName: String, count: Int, path: Path)

  // Acknowledge message for received websocket message
  val wsAckMessage = TextMessage("ACK")

  /**
   * Used to create the actor
   *
   * @param name the name of the client, for logging
   * @param streamName the name of the stream we are subscribed to
   * @param dir the directory in which to save the files received (if saveFiles is true)
   * @param clientActor an actor to write messages to when a file is received (using 'ask', actor should reply to each message)
   * @param outSink a sink to send to server to acknowledge the message, for flow control
   * @param saveFiles if true, save the files in the given dir (Set to false for throughput tests)
   */
  def props(name: String,
            streamName: String,
            dir: File,
            clientActor: ActorRef,
            outSink: Sink[Message, NotUsed],
            saveFiles: Boolean)(
      implicit system: ActorSystem
  ): Props =
    Props(new WebSocketActor(name, streamName, dir, clientActor, outSink, saveFiles))
}

/**
 * An actor that receives websocket messages from the VBDS server.
 */
class WebSocketActor(name: String,
                     streamName: String,
                     dir: File,
                     clientActor: ActorRef,
                     outSink: Sink[Message, NotUsed],
                     saveFiles: Boolean)(
    implicit val system: ActorSystem
) extends Actor
    with ActorLogging {

  import WebSocketActor._
  import system.dispatcher

  var count                = 0
  var file: File           = _
  var os: FileOutputStream = _
  implicit val askTimeout  = Timeout(20.seconds)

  log.info(s"$name: Started WebSocketActor")

  def receive: Receive = {
    case StreamInitialized ⇒
      log.info(s"$name: Initialized stream for $streamName")
      count = 0
      newFile()
      sender() ! Ack

    case bm: BinaryMessage ⇒
      val replyTo = sender()
      val f       = bm.dataStream.mapAsync(1)(bs => self ? HandleByteString(bs)).runWith(Sink.ignore)
      f.onComplete {
        case Success(_) =>
          replyTo ! Ack // ack to allow the stream to proceed sending more elements

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

    case HandleByteString(bs) =>
      sender() ! handleByteString(bs)
  }

  private def newFile(): Unit = {
    count = count + 1
    file = new File(dir, s"$streamName-$count")
    if (saveFiles) os = new FileOutputStream(file)
  }

  // Sends a reply on the websocket acknowledging that we received the contents of a file, to prevent overflow
  private def sendWsAck(): Unit = {
    Source.single(wsAckMessage).runWith(outSink)
  }

  // Called when a ByteString is received on the websocket
  private def handleByteString(bs: ByteString): Future[Unit] = {
    if (bs.size == 1 && bs.utf8String == "\n") {
      sendWsAck()
      if (saveFiles) {
        os.close()
        log.info(s"$name: Wrote $file")
      }
      val rf = ReceivedFile(streamName, count, file.toPath)
      val f  = clientActor ? rf
      newFile()
      f.map(_ => ())
    } else {
      if (saveFiles) os.write(bs.toArray)
      Future.successful(())
    }
  }

}
