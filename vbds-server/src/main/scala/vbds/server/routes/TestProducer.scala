package vbds.server.routes

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream._
import akka.stream.scaladsl.{Broadcast, BroadcastHub, FileIO, Flow, GraphDSL, Keep, MergeHub, RunnableGraph, Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._

object TestProducer extends App {
  implicit val system = ActorSystem()
  implicit val mat    = ActorMaterializer()

  //object XXX extends Directives {
  //  val database = new Database()
  //
  //  val measurementsWebSocketService =
  //    Flow[Message]
  //      .collect {
  //        case TextMessage.Strict(text) =>
  //          Future.successful(text)
  //        case TextMessage.Streamed(textStream) =>
  //          textStream.runFold("")(_ + _)
  //            .flatMap(Future.successful)
  //      }
  //      .mapAsync(1)(identity)
  //      .map(InsertMessage.parse)
  //      .groupedWithin(1000, 1 second)
  //      .mapAsync(10)(database.bulkInsertAsync)
  //      .map(messages => InsertMessage.ack(messages.last))
  //
  //  val route = path("measurements") {
  //    get {
  //      handleWebSocketMessages(measurementsWebSocketService)
  //    }
  //  }
  //
  //  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  //
  //}
  //

  object ReuseFlow {

    def test() = {
      val source: Source[Int, NotUsed] = Source(1 to 100)
      val factorials                   = source.scan(BigInt(1))((acc, next) ⇒ acc * next)

      def lineSink(filename: String): Sink[String, Future[IOResult]] =
        Flow[String]
          .map(s ⇒ ByteString(s + "\n"))
          .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

      factorials.map(_.toString).runWith(lineSink("factorial2.txt"))
    }

  }

  object BroadcastExample1 {
    def run() {
      println("\nBroadcastExample1:\n")
      val mySource                       = Source(1 to 10)
      val sink1: Sink[Int, Future[Done]] = Sink.foreach(i => println(s"sink1: $i"))
      val sink2: Sink[Int, Future[Done]] = Sink.foreach(i => println(s"sink2: $i"))
      val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val bcast = b.add(Broadcast[Int](4))
        mySource ~> bcast.in
        bcast.out(0) ~> Flow[Int] ~> sink1
        bcast.out(1) ~> Flow[Int].map(_ + 1) ~> sink2
        bcast.out(2).to(Sink.foreach(println))
        bcast.out(3).map(i => println(s"sink4: $i")).to(Sink.ignore)
        ClosedShape
      })
      g.run()
    }
  }

  object BroadcastExample2 {
    def run(): Unit = {
      println("\nBroadcastExample2:\n")
      //  val producer = source.toMat(BroadcastHub.sink(16))(Keep.right).run()
      //  producer.runForeach(x => println(s"1 Producer x: $x"))

      def broadcast[T](): Flow[T, T, UniqueKillSwitch] = {
        val (sink, source) = MergeHub
          .source[T](perProducerBufferSize = 16)
          .toMat(BroadcastHub.sink(bufferSize = 16))(Keep.both)
          .run()
        //    source.runWith(Sink.ignore)
        Flow
          .fromSinkAndSource(sink, source)
          .joinMat(KillSwitches.singleBidi[T, T])(Keep.right)
          .backpressureTimeout(3.seconds)
      }

      val mySource = Source(1 to 10)
      val flow     = broadcast[Int]()

      Source(1 to 10)
        .viaMat(flow)(Keep.right)
        .to(Sink.foreach(println))
        .run()

      Source(1 to 10)
        .viaMat(flow)(Keep.right)
        .to(Sink.foreach(println))
        .run()

      //  mySource
      //    .viaMat(flow)(Keep.right)
      //    .to(Sink.foreach(println))
      //    .run()

    }

  }

  object MatExample {
    val mySource = Source(1 to 10)
    def run(): Unit = {
      val count: Flow[Int, Int, NotUsed] = Flow[Int].map(_ ⇒ 1)

      val sumSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

      val counterGraph: RunnableGraph[Future[Int]] =
        mySource
          .via(count)
          .toMat(sumSink)(Keep.right)

      val sum: Future[Int] = counterGraph.run()

      sum.foreach(c ⇒ println(s"Total tweets processed: $c"))

    }
  }

  BroadcastExample1.run()
//  BroadcastExample2.run()
}
