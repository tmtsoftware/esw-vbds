package vbds.client

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, Sink, Source}
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
class WebSocketListener(implicit val system: ActorSystem) {

  import system.dispatcher

  /**
    * Opens a websocket to the server at the given URI and sends the messages received via websocket to the given actor.
    * @param uri a ws:// URL to open a websocket channel to receive messages from the server with the image data
    * @param actorRef the actor that will receive the websocket messages
    * @return an object containing the HTTP response and a promise that can be used to disconnect (which also unsubscribes)
    */
  def subscribe(uri: Uri, actorRef: ActorRef, outSource: Source[Message, NotUsed]): VbdsClient.Subscription = {

    // A sink that sends messages to the actor, with back pressure
    val inSink: Sink[Message, NotUsed] = Sink.actorRefWithBackpressure(
      actorRef,
      onInitMessage = WebSocketActor.StreamInitialized,
      ackMessage = WebSocketActor.Ack,
      onCompleteMessage = WebSocketActor.StreamCompleted,
      onFailureMessage = (ex: Throwable) => WebSocketActor.StreamFailure(ex)
    )

    // Creates a Flow from a Sink and a Source where the Flow's input will be sent to the Sink and
    // the Flow's output will come from the Source.
    val flow = Flow.fromSinkAndSource(inSink, outSource)

//    val (upgradeResponse, promise) = Http().singleWebSocketRequest(WebSocketRequest(uri), flow)
    val (upgradeResponse, _) = Http().singleWebSocketRequest(WebSocketRequest(uri), flow)

    // XXX TODO FIXME
    val promise: Promise[Option[Message]] = Promise[Option[Message]]()

    new SubscribeResult(upgradeResponse.map(_.response), promise)
  }

}
