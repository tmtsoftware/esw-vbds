package vbds.server.routes

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}

object TestProducer extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  val source = Source(1 to 10)
  val producer = source.toMat(BroadcastHub.sink(16))(Keep.right).run()
  producer.runForeach(x => println(s"1 Producer x: $x"))
}
