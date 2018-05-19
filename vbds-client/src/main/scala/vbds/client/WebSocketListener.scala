package vbds.client

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.{Future, Promise}
import akka.util.Timeout

import scala.concurrent.duration._

class WebSocketListener(implicit val system: ActorSystem, implicit val materializer: Materializer) {

  import system.dispatcher

//  implicit val askTimeout = Timeout(5.seconds)

  def subscribe(uri: Uri, actorRef: ActorRef): Future[HttpResponse] = {

    val sink: Sink[Message, NotUsed] = Sink.actorRefWithAck(
      actorRef,
      onInitMessage = WebSocketActor.StreamInitialized,
      ackMessage = WebSocketActor.Ack,
      onCompleteMessage = WebSocketActor.StreamCompleted,
      onFailureMessage = (ex: Throwable) ⇒ WebSocketActor.StreamFailure(ex)
    )

    // using Source.maybe materializes into a promise which will allow us to complete the source later
    val flow: Flow[Message, Message, Promise[Option[Message]]] =
      Flow.fromSinkAndSourceMat(sink, Source.maybe[Message])(Keep.right)

    val (upgradeResponse, promise) = Http().singleWebSocketRequest(WebSocketRequest(uri), flow)

    upgradeResponse.map(_.response)

    // at some later time we want to disconnect
    //    promise.success(None)
  }

}
