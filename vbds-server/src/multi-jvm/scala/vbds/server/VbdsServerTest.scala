package vbds.server

import java.io.{BufferedOutputStream, File, FileOutputStream}
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import vbds.client.VbdsClient
import vbds.server.app.VbdsServer

import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import csw.network.utils.SocketUtils
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import vbds.client.WebSocketActor.ReceivedFile

import scala.annotation.tailrec

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

  val configStr =
    """
      | akka.loglevel = INFO
      | akka.log-dead-letters-during-shutdown = off
      | akka.testconductor.barrier-timeout = 30m
      | akka.cluster.jmx.multi-mbeans-in-same-jvm = on
      | remote {
      |    artery {
      |      enabled = on
      |      transport = tcp
      |      canonical.port = 0
      |    }
      |    log-remote-lifecycle-events = off
      | }
      |""".stripMargin

  commonConfig(ConfigFactory.parseString(configStr).withFallback(ConfigFactory.load()))

//  def makeSystem(config: Config): akka.actor.ActorSystem = ActorSystem(SpawnProtocol(), VbdsServer.clusterName, config).classicSystem
  def makeSystem(config: Config) = akka.actor.ActorSystem(VbdsServer.clusterName, config)
}

// One for each role
class VbdsServerSpecMultiJvmServer1     extends VbdsServerTest
class VbdsServerSpecMultiJvmServer2     extends VbdsServerTest
class VbdsServerSpecMultiJvmSubscriber1 extends VbdsServerTest
class VbdsServerSpecMultiJvmSubscriber2 extends VbdsServerTest
class VbdsServerSpecMultiJvmPublisher1  extends VbdsServerTest

object VbdsServerTest {
  val seedPort        = 8888
  val server1HttpPort = 7777
  val server2HttpPort = server1HttpPort + 1
  val streamName      = "WFS1-RAW"
  val testFile        = new File(s"${getTempDir("vbds")}/vbdsTestFile")

  // --- Edit this ---
  // Image dimensions: 128x128, 256x256, 512x512, 1024x1024, 2048x2048, 4096x4096 (x 3)
//  val testFileDims = List(48, 128, 256, 512, 1024, 2048, 4096, 9216)
  val testFileDims = List(48, 128, 256, 512)
  // Repeat each test to get warmed up performance data
//  val repeatTests         = 5
  val repeatTests = 1
//  val numFilesToPublish   = 50
  val numFilesToPublish   = 10
  val totalFilesToPublish = testFileDims.size * repeatTests * numFilesToPublish
  val bytesPerPixel       = 4
  //        val bytesPerPixel = 2

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
//        log.debug(s"XXX receiveFile: $name: ${r.count} == $totalFilesToPublish, delay = $delay")
        if (r.count == totalFilesToPublish) {
          promise.success(r)
          self ! PoisonPill
        } else {
          if (delay != Duration.Zero) Thread.sleep(delay.toMillis)
        }
      } else {
        log.error(s"${r.path} and $testFile differ")
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

class VbdsServerTest
    extends MultiNodeSpec(VbdsServerTestConfig, VbdsServerTestConfig.makeSystem)
    with STMultiNodeSpec
    with ImplicitSender
    with BeforeAndAfterAll {

  import VbdsServerTestConfig.*
  import VbdsServerTest.*

  def initialParticipants = roles.size

  "The test" must {
    enterBarrier("startup")

    "start a server" in within(longTimeout) {
      runOn(server1) {
        val host = system.settings.config.getString("multinode.host")
        log.debug(s"server1 (seed node) is running on $host")

        // Start the first server (the seed node)
        val vbdsSystem = VbdsServer.start(
          host,
          server1HttpPort,
          seedPort,
          "server1",
          s"$host:$seedPort"
        )
        expectNoMessage(2.seconds)
        enterBarrier("deployed")
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        enterBarrier("receivedFiles")
        log.debug("server1: enterBarrier receivedFiles")
        vbdsSystem.terminate()
      }
    }

    "start a second server" in within(longTimeout) {
      runOn(server2) {
        val host       = system.settings.config.getString("multinode.host")
        val serverHost = system.settings.config.getString("multinode.server-host")
        log.debug(s"server2 is running on $host (seed node is $serverHost)")

        // Start a second server
        val vbdsSystem = VbdsServer.start(
          host,
          server2HttpPort,
          SocketUtils.getFreePort,
          "server2",
          s"$serverHost:$seedPort"
        )

        expectNoMessage(2.seconds)
        enterBarrier("deployed")
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        enterBarrier("receivedFiles")
        log.debug("server2: enterBarrier receivedFiles")
        vbdsSystem.terminate()
      }
    }

    "start a subscriber" in within(longTimeout) {
      runOn(subscriber1) {
        val host = system.settings.config.getString("multinode.host")
        log.debug(s"subscriber1 is running on $host")
        enterBarrier("deployed")
        val client = new VbdsClient("subscriber1", host, server1HttpPort)
        enterBarrier("streamCreated")
        log.debug("subscriber1: enterBarrier streamCreated")
        val promise      = Promise[ReceivedFile]()
        val clientActor  = system.actorOf(ClientActor.props("subscriber1", promise, log, subscriber1Delay))
        val subscription = client.subscribe(streamName, getTempDir("subscriber1"), clientActor, doCompareFiles)
        val httpResponse = subscription.httpResponse.await(shortTimeout)
        assert(httpResponse.status == StatusCodes.SwitchingProtocols)
        enterBarrier("subscribedToStream")
        log.debug("subscriber1: enterBarrier subscribedToStream")
        subscription.unsubscribe()
        promise.future.await(longTimeout)
        enterBarrier("receivedFiles")
        log.debug("subscriber1: enterBarrier receivedFiles")
      }
    }

    "start a second subscriber" in within(longTimeout) {
      runOn(subscriber2) {
        val host = system.settings.config.getString("multinode.host")
        log.debug(s"subscriber2 is running on $host")
        enterBarrier("deployed")
        val client = new VbdsClient("subscriber2", host, server2HttpPort)
        enterBarrier("streamCreated")
        log.debug("subscriber2: enterBarrier streamCreated")
        val promise     = Promise[ReceivedFile]()
        val clientActor = system.actorOf(ClientActor.props("subscriber2", promise, log, subscriber2Delay))

        // Server2 might need time to receive the distributed info, so need to loop here
        @tailrec
        def subscribe(): Unit = {
          Thread.sleep(500)
          val subscription = client.subscribe(streamName, getTempDir("subscriber2"), clientActor, doCompareFiles)
          val httpResponse = subscription.httpResponse.await(shortTimeout)
          if (httpResponse.status == StatusCodes.SwitchingProtocols) {
            enterBarrier("subscribedToStream")
            log.debug("subscriber2: enterBarrier subscribedToStream")
            promise.future.await(longTimeout)
            enterBarrier("receivedFiles")
            log.debug("subscriber2: enterBarrier receivedFiles")
          } else {
            subscribe()
          }
        }

        subscribe()
      }
    }

    "start a publisher" in within(longTimeout) {
      runOn(publisher1) {
        val host = system.settings.config.getString("multinode.host")
        log.debug(s"publisher1 is running on $host")
        enterBarrier("deployed")
        val client         = new VbdsClient("publisher1", host, server1HttpPort)
        val createResponse = client.createStream(streamName, "").await(shortTimeout)
        assert(createResponse.status == StatusCodes.OK)
        enterBarrier("streamCreated")
        log.debug("publisher1: enterBarrier streamCreated")
        enterBarrier("subscribedToStream")
        log.debug("publisher1: enterBarrier subscribedToStream")
        // Give the subscribers and other servers time to connect
        Thread.sleep(1000)

        (1 to repeatTests).foreach { count =>
          testFileDims.foreach { dim =>
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

            log.info(
              f"Step $count: Published $numFilesToPublish files of size $testFileSizeBytes [$dim x $dim x $bytesPerPixel] in $elapsedTimeSecs%1.3f secs ($hz%1.3f hz)"
            )
          }
        }

        enterBarrier("receivedFiles")
        log.debug("publisher1: enterBarrier receivedFiles")
      }
    }

    enterBarrier("finished")
    log.debug(s"enterBarrier finished")
  }
}
