package vbds.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import vbds.server.controllers.StreamAdmin

import scala.concurrent.Future

object VbdsServer {
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  def start(): Future[Http.ServerBinding] = Http().bindAndHandle(StreamAdmin.route, "localhost", 9999)

  def stop(binding: Http.ServerBinding): Unit = binding.unbind().onComplete(_ => system.terminate())
}
