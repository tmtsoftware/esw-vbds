package vbds.client.app
import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import scala.concurrent.blocking

import scala.concurrent.Future
import scala.io.StdIn

// XXX Temp Test
object HttpTestServer {
  implicit val system: ActorSystem = ActorSystem("http-slow-server")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  def main(args: Array[String]): Unit = {
    val route = extractUri { uri =>
      onSuccess(slowOp(uri)) {
        complete(StatusCodes.NoContent)
      }
    }

    val binding = Http().bindAndHandle(route, "0.0.0.0", 4000)
    println("Server online at http://0.0.0.0:4000/")
    StdIn.readLine()
    binding.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }

  def slowOp(requestUri: Uri): Future[Unit] = Future {
    blocking {
      println(s"[${LocalDateTime.now}] --> ${requestUri.authority.host}")
      Thread.sleep(5000)
    }
  }
}
