//package vbds.client.app
//import java.time.LocalDateTime
//
//import akka.actor.ActorSystem
//import akka.http.scaladsl.Http
//import akka.http.scaladsl.model.{HttpRequest, Uri}
//import akka.stream.scaladsl.{Sink, Source}
//
//import scala.util.{Failure, Success}
//
//// XXX Temp Test
//object HttpTestClient {
//  implicit val system: ActorSystem = ActorSystem("http-pool-test")
//
//  val connection = Http().cachedHostConnectionPool[Int]("localhost", 4000)
//
//  def main(args: Array[String]): Unit =
//    Source(1 to 64)
//      .map(i => (HttpRequest(uri = Uri("http://localhost:4000/")), i))
//      .via(connection)
//      .runWith(Sink.foreach {
//        case (Success(_), i) => println(s"[${LocalDateTime.now}] $i succeeded")
//        case (Failure(e), i) => println(s"[${LocalDateTime.now}] $i failed: $e")
//      })
//}
