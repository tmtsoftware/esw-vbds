package vbds.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import vbds.server.controllers.StreamAdmin

import scala.concurrent.Future

class VbdsServer(implicit system: ActorSystem) {
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  // XXX TODO: add other routes, possibly based on command line options ...
  val route = StreamAdmin.route

  // XXX TODO: configure port and host
  def start(): Future[Http.ServerBinding] =
    Http().bindAndHandle(route, "localhost", 9999)

  def stop(binding: Http.ServerBinding): Unit =
    binding.unbind().onComplete(_ => system.terminate())
}
