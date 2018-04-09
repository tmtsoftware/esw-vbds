package vbds.server.app

import akka.actor.ActorSystem
import vbds.server.VbdsServer

import scala.util.{Failure, Success}

object VbdsServerApp extends App {
  implicit val system = ActorSystem("vbds-system")
  import system.dispatcher
  new VbdsServer().start().onComplete {
    case Success(result) => println(result.localAddress)
    case Failure(error)  => println(error)
  }
}
