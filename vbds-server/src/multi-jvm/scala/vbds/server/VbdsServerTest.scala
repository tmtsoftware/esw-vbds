package vbds.server

import java.io.{BufferedOutputStream, File, FileOutputStream}

import akka.remote.testkit.MultiNodeConfig
import vbds.client.VbdsClient
import vbds.server.app.{VbdsServer, VbdsServerApp}

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, Future, Promise}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import TestFutureExtension._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import org.apache.commons.io.FileUtils
import vbds.client.WebSocketActor.ReceivedFile

object TestFutureExtension {

  implicit class RichFuture[T](val f: Future[T]) extends AnyVal {
    def await: T = Await.result(f, 20.seconds)

    def done: Future[T] = Await.ready(f, 20.seconds)
  }

}

object VbdsServerTestConfig extends MultiNodeConfig {
  val server1     = role("server1")
  val server2     = role("server2")
  val subscriber1 = role("subscriber1")
  val subscriber2 = role("subscriber2")
  val publisher1  = role("publisher1")
//  val publisher2 = role("publisher2")
}

class VbdsServerSpecMultiJvmServer1     extends VbdsServerTest
class VbdsServerSpecMultiJvmServer2     extends VbdsServerTest
class VbdsServerSpecMultiJvmSubscriber1 extends VbdsServerTest
class VbdsServerSpecMultiJvmSubscriber2 extends VbdsServerTest
class VbdsServerSpecMultiJvmPublisher1  extends VbdsServerTest
//class VbdsServerSpecMultiJvmPublisher2 extends VbdsServerTest

object VbdsServerTest {
  val host              = "127.0.0.1"
  val seedPort          = 8888
  val server1HttpPort   = 7777
  val server2HttpPort   = server1HttpPort + 1
  val streamName        = "MyStream"
  val testFileName      = "vbdsTestFile"
  val testFileSizeBytes = 16 * 1024 * 1024
  val numFilesToPublish = 20

  val testFile = makeFile(testFileSizeBytes, testFileName)
  testFile.deleteOnExit()

  private def getTempDir(name: String): String = {
    val dir = s"${System.getProperty("java.io.tmpdir")}/$name"
    new File(dir).mkdir()
    dir
  }

  // Returns a queue for receiving files via websocket.
  // The given function will be called for each received file.
  def makeQueue(name: String, promise: Promise[ReceivedFile])(implicit system: ActorSystem,
                                                              mat: Materializer): SourceQueueWithComplete[ReceivedFile] = {
    Source
      .queue[ReceivedFile](3, OverflowStrategy.backpressure)
      .buffer(2, OverflowStrategy.backpressure)
      .map { r =>
        println(s"$name: Received file ${r.count}: ${r.path} for stream ${r.streamName}")
        if (FileUtils.contentEquals(r.path.toFile, testFile)) {
          println(s"${r.path} and $testFile are equal")
          if (r.count >= numFilesToPublish) promise.success(r)
        } else {
          println(s"${r.path} and $testFile differ")
          promise.failure(new RuntimeException(s"${r.path} and $testFile differ"))
        }
        r.path.toFile.delete()
      }
      .to(Sink.ignore)
      .run()
  }

  // Make a temp file with numBytes bytes of data and the given base name
  def makeFile(numBytes: Int, name: String): File = {
    val file = new File(s"${getTempDir("vbds")}/$name")
    val os   = new BufferedOutputStream(new FileOutputStream(file))
    (0 to numBytes).foreach(i => os.write(i))
    os.close()
    file
  }
}

class VbdsServerTest extends MultiNodeSpec(VbdsServerTestConfig) with STMultiNodeSpec with ImplicitSender {

  import VbdsServerTestConfig._
  import VbdsServerTest._

  def initialParticipants = roles.size

  "A VbdsServerTest" must {

    "wait for all server nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "Allow creating a stream, subscribing and publishing to a stream" in {
      runOn(server1) {
//        VbdsServer.start(host, server1HttpPort).await
        VbdsServerApp.main(Array("--http-port", s"$server1HttpPort", "--akka-port", s"$seedPort", "-s", s"$host:$seedPort"))
        expectNoMessage(2.seconds)
        enterBarrier("deployed")
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        enterBarrier("receivedFiles")
      }

      runOn(server2) {
//        VbdsServer.start(host, server2HttpPort).await
        VbdsServerApp.main(Array("--http-port", s"$server2HttpPort", "-s", s"$host:$seedPort"))
        expectNoMessage(2.seconds)
        enterBarrier("deployed")
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        enterBarrier("receivedFiles")
      }

      runOn(subscriber1) {
        implicit val materializer = ActorMaterializer()
        enterBarrier("deployed")
        val client = new VbdsClient(host, server1HttpPort)
        enterBarrier("streamCreated")
        val promise           = Promise[ReceivedFile]
        val queue             = makeQueue("subscriber1", promise)
        val subscribeResponse = client.subscribe(streamName, getTempDir("subscriber1"), queue).await
//        assert(subscribeResponse.status == StatusCodes.OK)
        println(s"subscriber1: Subscribe response = $subscribeResponse, content type: ${subscribeResponse.entity.contentType}")
        enterBarrier("subscribedToStream")
        promise.future.await
        enterBarrier("receivedFiles")
      }

      runOn(subscriber2) {
        implicit val materializer = ActorMaterializer()
        enterBarrier("deployed")
        val client = new VbdsClient(host, server2HttpPort)
        enterBarrier("streamCreated")
        val promise           = Promise[ReceivedFile]
        val queue             = makeQueue("subscriber2", promise)
        val subscribeResponse = client.subscribe(streamName, getTempDir("subscriber2"), queue).await
//        assert(subscribeResponse.status == StatusCodes.OK)
        println(s"subscriber2: Subscribe response = $subscribeResponse, content type: ${subscribeResponse.entity.contentType}")
        enterBarrier("subscribedToStream")
        promise.future.await
        enterBarrier("receivedFiles")
      }

      runOn(publisher1) {
        implicit val materializer = ActorMaterializer()
        enterBarrier("deployed")
        val client         = new VbdsClient(host, server1HttpPort)
        val createResponse = client.createStream(streamName).await
        assert(createResponse.status == StatusCodes.OK)
        println(s"publisher1: Create response = $createResponse, content type: ${createResponse.entity.contentType}")
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        (1 to numFilesToPublish).foreach { _ =>
          client.publish(streamName, testFile).await
//          Thread.sleep(100) // XXX TODO FIXME
        }
        enterBarrier("receivedFiles")
      }

//      runOn(publisher2) {
//        implicit val materializer = ActorMaterializer()
//        enterBarrier("deployed")
//        val client = new VbdsClient(host, server1HttpPort)
//        enterBarrier("streamCreated")
//        enterBarrier("subscribedToStream")
//        enterBarrier("receivedFiles")
//      }

      enterBarrier("finished")
    }
  }
}
