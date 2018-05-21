package vbds.client

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.{Future, Promise}

/**
  * Implements client subscription, which opens a websocket to the server
  */
class WebSocketListener(implicit val system: ActorSystem, implicit val materializer: Materializer) {

  import system.dispatcher

  /**
    * Opens a websocket to the server at the given URI and sends the messages received via websocket to the given actor.
    * @param uri a ws:// URL to open a websocket channel to receive messages from the server with the image data
    * @param actorRef the actor that will receive the websocket messages
    * @return the HTTP response
    */
  def subscribe(uri: Uri, actorRef: ActorRef): Future[HttpResponse] = {
    // A sink that sends messages to the actor, with back pressure
    val sink: Sink[Message, NotUsed] = Sink.actorRefWithAck(
      actorRef,
      onInitMessage = WebSocketActor.StreamInitialized,
      ackMessage = WebSocketActor.Ack,
      onCompleteMessage = WebSocketActor.StreamCompleted,
      onFailureMessage = (ex: Throwable) ⇒ WebSocketActor.StreamFailure(ex)
    )

    // Using Source.maybe materializes into a promise which will allow us to complete the source later
    val flow: Flow[Message, Message, Promise[Option[Message]]] =
      Flow.fromSinkAndSourceMat(sink, Source.maybe[Message])(Keep.right)

    val (upgradeResponse, promise) = Http().singleWebSocketRequest(WebSocketRequest(uri), flow)

    upgradeResponse.map(_.response)

    // at some later time we want to disconnect
    //    promise.success(None)
  }

}
