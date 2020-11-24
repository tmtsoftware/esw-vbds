package vbds.server

import java.io.{BufferedOutputStream, File, FileOutputStream}

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.remote.testkit.MultiNodeConfig
import vbds.client.VbdsClient
import vbds.server.app.VbdsServer

import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import vbds.client.WebSocketActor.ReceivedFile

// Tests with multiple servers, publishers and subscribers

// Note: To test with remote hosts, set multiNodeHosts environment variable to comma separated list of hosts.
// For example: setenv multiNodeHosts "192.168.178.77,username@192.168.178.36"
// and run: sbt multiNodeTest
//
// To test locally on different JVMs, run: sbt multi-jvm:test

object VbdsServerTestConfig extends MultiNodeConfig {
  val server1     = role("server1")
  val server2     = role("server2")
  val subscriber1 = role("subscriber1")
  val subscriber2 = role("subscriber2")
  val publisher1  = role("publisher1")
  //  val publisher2 = role("publisher2")

  commonConfig(ConfigFactory.parseString("""
      | akka.loglevel = INFO
      | akka.log-dead-letters-during-shutdown = off
      | akka.testconductor.barrier-timeout = 30m
    """))

}

class VbdsServerSpecMultiJvmServer1 extends VbdsServerTest("server1")

class VbdsServerSpecMultiJvmServer2 extends VbdsServerTest("server2")

class VbdsServerSpecMultiJvmSubscriber1 extends VbdsServerTest("subscriber1")

class VbdsServerSpecMultiJvmSubscriber2 extends VbdsServerTest("subscriber2")

class VbdsServerSpecMultiJvmPublisher1 extends VbdsServerTest("publisher1")

//class VbdsServerSpecMultiJvmPublisher2 extends VbdsServerTest

object VbdsServerTest {
  val seedPort        = 8888
  val server1HttpPort = 7777
  val server2HttpPort = server1HttpPort + 1
  val streamName      = "WFS1-RAW"
  val testFile        = new File(s"${getTempDir("vbds")}/vbdsTestFile")

  // --- Edit this ---
  // Image dimensions: 128x128, 256x256, 512x512, 1024x1024, 2048x2048, 4096x4096 (x 3)
  val testFileDims = List(48, 128, 256, 512, 1024, 2048, 4096, 9216)
  // Repeat each test to get warmed up performance data
  val repeatTests         = 5
  val numFilesToPublish   = 50
  val totalFilesToPublish = testFileDims.size * repeatTests * numFilesToPublish

  // ---

  val shortTimeout = 60.seconds
  val longTimeout  = 10.hours // in case you want to test with lots of files...

  // Simulate a slow publisher/subscriber (XXX Not sure simulated slow subscriber is working correctly)
  val publisherDelay   = Duration.Zero
  val subscriber1Delay = Duration.Zero
  val subscriber2Delay = Duration.Zero

  // If true, compare files to make sure the file was transferred correctly
  val doCompareFiles = false

  private def getTempDir(name: String): File = {
    val dir = new File(s"${System.getProperty("java.io.tmpdir")}/$name")
    dir.mkdir()
    dir
  }

  // Actor to receive and acknowledge files
  private object ClientActor {
    def props(name: String, promise: Promise[ReceivedFile], log: LoggingAdapter, delay: FiniteDuration): Props =
      Props(new ClientActor(name, promise, log, delay))
  }

  private class ClientActor(name: String, promise: Promise[ReceivedFile], log: LoggingAdapter, delay: FiniteDuration)
      extends Actor
      with ActorLogging {

    // Called when a file is received by the named subscriber.
    // Checks the file contents (if doCompareFiles is true) and prints statistics when done.
    // Received (temp) files are deleted.
    private def receiveFile(name: String, r: ReceivedFile, promise: Promise[ReceivedFile], delay: FiniteDuration): Unit = {

      if (!doCompareFiles || FileUtils.contentEquals(r.path.toFile, testFile)) {
        if (r.count == totalFilesToPublish) {
          promise.success(r)
          self ! PoisonPill
        } else {
          if (delay != Duration.Zero) Thread.sleep(delay.toMillis)
        }
      } else {
        println(s"${r.path} and $testFile differ")
        promise.failure(new RuntimeException(s"${r.path} and $testFile differ"))
      }
      r.path.toFile.delete()
    }

    def receive: Receive = {
      case r: ReceivedFile =>
        receiveFile(name, r, promise, delay)
        sender() ! r

      case x => log.warning(s"Unexpected message: $x")
    }
  }

  // Make a temp file with numBytes bytes of data and the given base name
  def makeFile(numBytes: Int, file: File): Unit = {
    if (file.exists()) file.delete()
    val os = new BufferedOutputStream(new FileOutputStream(file))
    (0 to numBytes).foreach(i => os.write(i))
    os.close()
    file.deleteOnExit()
  }

  implicit class RichFuture[T](val f: Future[T]) extends AnyVal {
    def await(timeout: FiniteDuration): T = Await.result(f, timeout)
  }
}

class VbdsServerTest(name: String)
    extends MultiNodeSpec(VbdsServerTestConfig)
    with STMultiNodeSpec
    with ImplicitSender
    with BeforeAndAfterAll {

  import VbdsServerTestConfig._
  import VbdsServerTest._

  def initialParticipants = roles.size

  enterBarrier("startup")

  runOn(server1) {
    val host = system.settings.config.getString("multinode.host")
    println(s"server1 (seed node) is running on $host")

    // Start the first server (the seed node)
    val vbdsSystem = VbdsServer.start(
      host,
      server1HttpPort,
      host,
      seedPort,
      s"$host:$seedPort"
    )
    expectNoMessage(2.seconds)
    enterBarrier("deployed")
    enterBarrier("streamCreated")
    enterBarrier("subscribedToStream")
    within(longTimeout) {
      enterBarrier("receivedFiles")
      println("server1: enterBarrier receivedFiles")
      vbdsSystem.terminate()
    }
  }

  runOn(server2) {
    val host       = system.settings.config.getString("multinode.host")
    val serverHost = system.settings.config.getString("multinode.server-host")
    println(s"server2 is running on $host (seed node is $serverHost)")

    // Start a second server
    val vbdsSystem = VbdsServer.start(
      host,
      server2HttpPort,
      host,
      0,
      s"$serverHost:$seedPort"
    )

    expectNoMessage(2.seconds)
    enterBarrier("deployed")
    enterBarrier("streamCreated")
    enterBarrier("subscribedToStream")
    within(longTimeout) {
      enterBarrier("receivedFiles")
      println("server2: enterBarrier receivedFiles")
      vbdsSystem.terminate()
    }
  }

  runOn(subscriber1) {
    val host = system.settings.config.getString("multinode.host")
    println(s"subscriber1 is running on $host")
    enterBarrier("deployed")
    val client = new VbdsClient("subscriber1", host, server1HttpPort)
    enterBarrier("streamCreated")
    val promise      = Promise[ReceivedFile]()
    val clientActor  = system.actorOf(ClientActor.props("subscriber1", promise, log, subscriber1Delay))
    val subscription = client.subscribe(streamName, getTempDir("subscriber1"), clientActor, doCompareFiles)
    val httpResponse = subscription.httpResponse.await(shortTimeout)
    assert(httpResponse.status == StatusCodes.SwitchingProtocols)
    enterBarrier("subscribedToStream")
    subscription.unsubscribe()
    promise.future.await(longTimeout)
    within(shortTimeout) {
      enterBarrier("receivedFiles")
      println("subscriber1: enterBarrier receivedFiles")
    }
  }

  runOn(subscriber2) {
    val host = system.settings.config.getString("multinode.host")
    println(s"subscriber2 is running on $host")
    enterBarrier("deployed")
    val client = new VbdsClient("subscriber2", host, server2HttpPort)
    enterBarrier("streamCreated")
    val promise      = Promise[ReceivedFile]()
    val clientActor  = system.actorOf(ClientActor.props("subscriber2", promise, log, subscriber2Delay))
    val subscription = client.subscribe(streamName, getTempDir("subscriber2"), clientActor, doCompareFiles)
    val httpResponse = subscription.httpResponse.await(shortTimeout)
    assert(httpResponse.status == StatusCodes.SwitchingProtocols)
    enterBarrier("subscribedToStream")
    promise.future.await(longTimeout)
    within(shortTimeout) {
      enterBarrier("receivedFiles")
      println("subscriber2: enterBarrier receivedFiles")
    }
  }

  runOn(publisher1) {
    val host = system.settings.config.getString("multinode.host")
    println(s"publisher1 is running on $host")
    enterBarrier("deployed")
    val client         = new VbdsClient("publisher1", host, server1HttpPort)
    val createResponse = client.createStream(streamName, "").await(shortTimeout)
    assert(createResponse.status == StatusCodes.OK)
    enterBarrier("streamCreated")
    enterBarrier("subscribedToStream")

    (1 to repeatTests).foreach { _ =>
      testFileDims.foreach { dim =>
        val bytesPerPixel = 4
//        val bytesPerPixel = 2
        val testFileSizeBytes = dim * dim * bytesPerPixel + 16500
        makeFile(testFileSizeBytes, testFile)

        val startTime = System.currentTimeMillis()

        Source(1 to numFilesToPublish)
          .mapAsync(1) { _ =>
            client.publish(streamName, testFile)
          }
          .runWith(Sink.ignore)
          .await(longTimeout)

        val elapsedTimeSecs = (System.currentTimeMillis() - startTime) / 1000.0
        val hz              = numFilesToPublish / elapsedTimeSecs

        println(f"Published $numFilesToPublish files of size $testFileSizeBytes [$dim x $dim x $bytesPerPixel] in $elapsedTimeSecs%1.3f secs ($hz%1.3f hz)")
      }
    }

    within(shortTimeout) {
      enterBarrier("receivedFiles")
      println("publisher1: enterBarrier receivedFiles")
    }
  }

  enterBarrier("finished")
}
