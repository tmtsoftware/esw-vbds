package vbds.server.actors
import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString

// XXX Temp to check FITS files
object CheckFitsActor {

  sealed trait CheckFitsActorMessage

  case object Ack extends CheckFitsActorMessage

  case object StreamInitialized extends CheckFitsActorMessage

  case object StreamCompleted extends CheckFitsActorMessage

  final case class StreamFailure(ex: Throwable) extends CheckFitsActorMessage

  // Reply to sender when stream completed
  case object WhenStreamCompleted extends CheckFitsActorMessage

  def props(): Props = Props(new CheckFitsActor())
}

private class CheckFitsActor() extends Actor with ActorLogging {
  import CheckFitsActor._

  var buffer: ByteString = ByteString.empty
  var count = 1
//  var replyTo: ActorRef = _

  def receive: Receive = {
    case bs: ByteString =>
      log.info(s"FITS check: Received ${bs.size} bytes")
      if (bs.size == 1 && bs.utf8String == "\n") {
        if (!buffer.take(80).utf8String.startsWith("SIMPLE"))
          log.warning(s"XXX ================> File $count is not a FITS file")
        count = count + 1
        buffer = ByteString.empty
      } else {
        log.info(s"XXX File $count: Received ${bs.size} bytes")
        buffer = buffer ++ bs
      }
      Thread.sleep(1000)
      sender() ! Ack

    case StreamInitialized ⇒
      log.info("Stream initialized")
      buffer = ByteString.empty
      sender() ! Ack

    case StreamCompleted ⇒
      log.info("Stream completed")
//      replyTo ! Done

    case StreamFailure(ex) ⇒
      log.error(ex, s"Stream failed!")
//      replyTo ! Done

//    case WhenStreamCompleted ⇒
//      replyTo = sender()
  }
}
