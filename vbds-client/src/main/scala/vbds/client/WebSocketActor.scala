package vbds.client

import java.io.{File, FileOutputStream}

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import scala.concurrent.duration._
import scala.util._

object WebSocketActor {

  case object Ack

  case object StreamInitialized

  case object StreamCompleted

  final case class StreamFailure(ex: Throwable)

  def props(streamName: String, dir: File)(implicit system: ActorSystem, mat: Materializer): Props =
    Props(new WebSocketActor(streamName, dir))
}

class WebSocketActor(streamName: String, dir: File)(implicit val system: ActorSystem, implicit val mat: Materializer)
    extends Actor
    with ActorLogging {

  import WebSocketActor._
  import system.dispatcher

  var count                = 0
  var file: File           = _
  var os: FileOutputStream = _
  implicit val askTimeout  = Timeout(5.seconds)

  log.info("Started WebSocketActor")

  def receive: Receive = {
    case StreamInitialized ⇒
      log.info(s"Initialized stream for $streamName")
      count = 0
      newFile()
      sender() ! Ack

    case msg: Message ⇒
      msg match {
        case bm: BinaryMessage =>
          log.info(s"Received binary message for stream $streamName")
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
        os.close()
        log.info(s"Wrote $file")
        newFile()
      } else {
        log.info(s"XXX writing ${bs.size} bytes to $streamName")
        os.write(bs.toArray)
      }
      sender() ! Ack // ack to allow the stream to proceed sending more elements

    case StreamCompleted ⇒
      log.info("Stream completed")

    case StreamFailure(ex) ⇒
      log.error(ex, "Stream failed!")
  }

  private def newFile(): Unit = {
    count = count + 1
    file = new File(dir, s"$streamName-$count")
    os = new FileOutputStream(file)
  }
}
