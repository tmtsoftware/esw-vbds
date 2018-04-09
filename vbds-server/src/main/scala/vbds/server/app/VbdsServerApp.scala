package vbds.server.app

import vbds.server.VbdsServer

import scala.util.{Failure, Success}

object VbdsServerApp extends App {
  import VbdsServer.system.dispatcher
  VbdsServer.start().onComplete {
    case Success(result) => println(result.localAddress)
    case Failure(error) => println(error)
  }
}
