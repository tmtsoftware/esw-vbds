package vbds.server.routes

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream._
import akka.stream.scaladsl.{Broadcast, BroadcastHub, FileIO, Flow, GraphDSL, Keep, Merge, MergeHub, RunnableGraph, Sink, Source}
import akka.util.ByteString

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object TestProducer extends App {
  implicit val system = ActorSystem()
  implicit val mat    = ActorMaterializer()
  import system.dispatcher

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

  object BroadcastExample3 {
    def run(): Unit = {
      import GraphDSL.Implicits._
      val sinks = immutable.Seq("a", "b", "c").map(prefix ⇒
        Flow[String].filter(str ⇒ str.startsWith(prefix)).toMat(Sink.head[String])(Keep.right)
      )

      val g: RunnableGraph[Seq[Future[String]]] = RunnableGraph.fromGraph(GraphDSL.create(sinks) { implicit b ⇒ sinkList ⇒
        val broadcast = b.add(Broadcast[String](sinkList.size))

        Source(List("ax", "bx", "cx")) ~> broadcast
        sinkList.foreach(sink ⇒ broadcast ~> sink)

        ClosedShape
      })

      val matList: Seq[Future[String]] = g.run()
    }
  }

  object BroadcastExample1 {
    def run() {
      import GraphDSL.Implicits._
      println("\nBroadcastExample1:\n")
      val mySource                       = Source(1 to 10)
      val sink1: Sink[Int, Future[Done]] = Sink.foreach(i => println(s"sink1: $i"))
      val sink2: Sink[Int, Future[Done]] = Sink.foreach(i => println(s"sink2: $i"))

      //      val source = Source(1 to 10)
      //      val sink = Sink.fold[Int, Int](0)(_ + _)
      //
      //      // connect the Source to the Sink, obtaining a RunnableGraph
      //      val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)
      //
      //      // materialize the flow and get the value of the FoldSink
      //      val sum: Future[Int] = runnable.run()

      val outX = Sink.ignore

      val g = RunnableGraph.fromGraph(GraphDSL.create(outX) { implicit builder => out =>
        import GraphDSL.Implicits._
        val in = Source(1 to 10)

        val bcast = builder.add(Broadcast[Int](2))
        val merge = builder.add(Merge[Int](2))

        val f1, f2, f4 = Flow[Int].map{i => println(s"YYY $i"); i}
        val f3 = Flow[Int].map(i => println(s"XXX $i"))

//        in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
//        bcast ~> f4 ~> merge

//        in ~> f1 ~> bcast
//        bcast ~> f2 ~> merge
//        bcast ~> f4 ~> merge
//        merge ~> f3 ~> out

        in ~> bcast
        bcast ~> f2 ~> merge
        bcast ~> f4 ~> merge
        merge ~> f3 ~> out


        ClosedShape
      })

      val x = g.run()
      x.onComplete {
        case Success(_) => println("BroadcastExample1 done")
        case Failure(ex) => ex.printStackTrace()
      }
    }
  }

  object BroadcastExample2 {
    def run(): Unit = {
      println("\nBroadcastExample2:\n")
      //  val producer = source.toMat(BroadcastHub.sink(16))(Keep.right).run()
      //  producer.runForeach(x => println(s"1 Producer x: $x"))

      def broadcastFlow[T](): Flow[T, T, UniqueKillSwitch] = {
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

      val mySource = Source(1 to 20)
      val flow     = broadcastFlow[Int]()

      mySource
        .viaMat(flow)(Keep.right)
        .to(Sink.foreach(println))
        .run()


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

//  BroadcastExample1.run()
    BroadcastExample2.run()
}
