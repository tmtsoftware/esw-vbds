//package vbds.misc
//
//import akka.Done
//import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
//import akka.stream.{ActorMaterializer, ClosedShape}
//import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergeHub, RunnableGraph, Sink, Source}
//import akka.testkit.TestKit
//import akka.util.{ByteString, Timeout}
//import com.typesafe.config.ConfigFactory
//import org.scalatest.FunSuiteLike
//
//import scala.concurrent.{Await, Future}
//import scala.concurrent.duration._
//import scala.util.{Failure, Success}
//import akka.pattern.ask
//
//object StreamTest2 {
//  // Actor to receive and acknowledge files
//  private object TestActor {
//    def props(): Props = Props(new TestActor())
//  }
//
//  private class TestActor() extends Actor with ActorLogging {
//    def receive: Receive = {
//      case x =>
//        println(s"Received: $x")
//        Thread.sleep(2000)
//        sender() ! x
//    }
//  }
//
//}
//
//// Place to experiment with streams
//class StreamTest2 extends TestKit(ActorSystem("test", ConfigFactory.parseString(""))) with FunSuiteLike {
//  import StreamTest2._
//
//  implicit val mat = ActorMaterializer()
//  import system.dispatcher
//
//  val (sink, source)  = MergeHub.source[Int](1).preMaterialize()
//  source.runForeach(i => println(s"MergeHub source received $i"))
//
//  def runGraph(t: Int, testActor: ActorRef): Future[Done] = {
//    val g = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => out =>
//      import GraphDSL.Implicits._
//      implicit val timeout = Timeout(20.seconds)
//      val numOut           = 1
//      val source           = Source(1 to 5)
//
//      // Broadcast with an output for each subscriber
//      val bcast = builder.add(Broadcast[Int](numOut))
//
//      // Merge afterwards to get a single output
//      val merge = builder.add(Merge[Int](numOut))
//
////      val sink1 = Sink.foreach[Int](i => println(s"sink1 => $t: $i"))
//
//      val flow1 = Flow[Int]
//        .alsoTo(sink)
//        .mapAsync(1) { i =>
//          if (i == 5) {
//            (testActor ? "flow1 => $t: $i").map(_ => i)
//          } else Future.successful(i)
//        }
//
//      source ~> bcast
//      bcast ~> flow1 ~> merge
//      merge ~> out
//      ClosedShape
//    })
//
//    val f = g.run()
//    f.onComplete {
//      case Success(_)  => println("Stream complete")
//      case Failure(ex) => println(s"Stream failed with $ex")
//    }
//    f
//  }
//
//  test("test graph completion") {
//    val testActor = system.actorOf(TestActor.props())
//    val f = Source(1 to 3)
//      .mapAsync(1)(i => runGraph(i, testActor))
//      .runWith(Sink.ignore)
//
//    Await.result(f, 20.seconds)
//    println("Done")
//  }
//
//}
