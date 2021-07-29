package vbds.client
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object StreamTest extends App {
  implicit val system       = ActorSystem("StreamTest")
  import system._

  val source         = Source(0 to 5)
  val runnableGraph  = source.toMat(BroadcastHub.sink(bufferSize = 1))(Keep.right)
  val reusableSource = runnableGraph.run()

  val a = reusableSource
    .mapAsync(1) { i =>
      Future { println(s"A: $i") }
    }
    .runWith(Sink.ignore)

  val b = reusableSource
    .alsoTo(Sink.foreach(i => println(s"B1: $i")))
    .mapAsync(1) { i =>
      Future { Thread.sleep(100); println(s"B2: $i") }
    }
    .runWith(Sink.ignore)

  val c = reusableSource.runWith(Sink.ignore)

  Future.sequence(List(a, b, c)).onComplete {
    case Success(_) =>
      println("OK")
      system.terminate()
    case Failure(ex) =>
      println(s"Error: $ex")
      system.terminate()
  }
}
