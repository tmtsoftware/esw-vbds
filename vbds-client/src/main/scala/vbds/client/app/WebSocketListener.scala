package vbds.client.app

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.{Future, Promise}

class WebSocketListener(implicit val system: ActorSystem, implicit val materializer: Materializer) {

  import system.dispatcher

  def subscribe(uri: Uri, handler: (Message) => Unit): Future[HttpResponse] = {
    println(s"XXX subscribe to $uri")
    // using Source.maybe materializes into a promise which will allow us to complete the source later
    val flow: Flow[Message, Message, Promise[Option[Message]]] = Flow.fromSinkAndSourceMat(
      Sink.foreach[Message](handler),
      Source.maybe[Message])(Keep.right)

    val (upgradeResponse, promise) = Http().singleWebSocketRequest(WebSocketRequest(uri), flow)

    val resp = upgradeResponse.map(_.response)
    resp.foreach(r => println(s"XXX subscribe response = $r"))
    resp

    // at some later time we want to disconnect
    //    promise.success(None)
  }

}
