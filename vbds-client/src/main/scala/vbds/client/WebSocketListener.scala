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

  implicit val askTimeout = Timeout(5.seconds)

  def subscribe(uri: Uri, actorRef: ActorRef): Future[HttpResponse] = {
    println(s"XXX subscribe to $uri")

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

    //    val flow: Flow[Message, Message, Promise[Option[Message]]] = Flow.fromSinkAndSourceMat(
    //      Sink.foreach[Message](actorRef ? _), // XXX TODO FIXME
    //      Source.maybe[Message])(Keep.right)

    val (upgradeResponse, promise) = Http().singleWebSocketRequest(WebSocketRequest(uri), flow)

    val resp = upgradeResponse.map(_.response)
    resp.foreach(r => println(s"XXX subscribe response = $r"))
    resp

    // at some later time we want to disconnect
    //    promise.success(None)
  }

}
