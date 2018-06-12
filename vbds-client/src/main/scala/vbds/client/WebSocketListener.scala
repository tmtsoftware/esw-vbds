package vbds.client

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import vbds.client.WebSocketListener.SubscribeResult

import scala.concurrent.{Future, Promise}

object WebSocketListener {

  /**
    * Result of subscribing to a stream
    * @param httpResponse response from server
    * @param promise used to close and unsubscribe
    */
  class SubscribeResult(override val httpResponse: Future[HttpResponse], promise: Promise[Option[Message]]) extends VbdsClient.Subscription {
    override def unsubscribe(): Unit = {
      promise.success(None)
    }
  }
}

/**
  * Implements client subscription, which opens a websocket to the server
  */
class WebSocketListener(implicit val system: ActorSystem, implicit val materializer: Materializer) {

  import system.dispatcher

  /**
    * Opens a websocket to the server at the given URI and sends the messages received via websocket to the given actor.
    * @param uri a ws:// URL to open a websocket channel to receive messages from the server with the image data
    * @param actorRef the actor that will receive the websocket messages
    * @return an object containing the HTTP response and a promise that can be used to disconnect (which also unsubscribes)
    */
  def subscribe(uri: Uri, actorRef: ActorRef): VbdsClient.Subscription = {
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
    new SubscribeResult(upgradeResponse.map(_.response), promise)
  }

}
