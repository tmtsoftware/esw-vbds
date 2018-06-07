package vbds.server

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.net.InetAddress

import akka.actor.ActorSystem
import akka.remote.testkit.MultiNodeConfig
import vbds.client.VbdsClient
import vbds.server.app.VbdsServerApp

import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.remote.RemoteTransportException
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import org.jboss.netty.channel.ChannelException
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

  val streamName = "WFS1-RAW"

  val testFileName = "vbdsTestFile"
//    val testFileSizeKb    = 300000
  val testFileSizeKb    = 1000
  val testFileSizeBytes = testFileSizeKb * 1000
  val numFilesToPublish = 1000

  val shortTimeout = 60.seconds
  val longTimeout  = 10.hours // in case you want to test with lots of files...

  // Simulate a slow publisher/subscriber (XXX Not sure simulated slow subscriber is working correctly)
//  val publisherDelay = 100.millis
//  val subscriber1Delay = 50.millis
//  val subscriber2Delay = 35.millis
  val publisherDelay   = Duration.Zero
  val subscriber1Delay = Duration.Zero
  val subscriber2Delay = Duration.Zero

  // If true, compare files to make sure the file was transferred correctly
  val doCompareFiles = false

  val testFile = makeFile(testFileSizeBytes, testFileName)
  testFile.deleteOnExit()

  private def getTempDir(name: String): File = {
    val dir = new File(s"${System.getProperty("java.io.tmpdir")}/$name")
    dir.mkdir()
    dir
  }

  // Called when a file is received. Checks the file contents (if doCompareFiles is true) and
  // prints statistics when done. Received (temp) files are deleted.
  private def receiveFile(name: String,
                          r: ReceivedFile,
                          promise: Promise[ReceivedFile],
                          startTime: Long,
                          delay: FiniteDuration): Unit = {
    println(s"$name: Received file ${r.count}: ${r.path}")
    if (!doCompareFiles || FileUtils.contentEquals(r.path.toFile, testFile)) {
      if (r.count >= numFilesToPublish) {
        val testSecs    = (System.currentTimeMillis() - startTime) / 1000.0
        val secsPerFile = testSecs / numFilesToPublish
        val mbPerSec    = (testFileSizeKb / 1000.0 * numFilesToPublish) / testSecs
        val hz          = 1.0 / secsPerFile
        println(f"""
             |
             | ===================================================
             | * $name: Received $numFilesToPublish $testFileSizeKb kb files in $testSecs seconds ($secsPerFile%1.3f secs per file, $hz%1.3f hz, $mbPerSec%1.3f mb/sec)
             | ===================================================
         """.stripMargin)
        promise.success(r)
      }
      if (delay != Duration.Zero) Thread.sleep(delay.toMillis)
    } else {
      println(s"${r.path} and $testFile differ")
      promise.failure(new RuntimeException(s"${r.path} and $testFile differ"))
    }
    r.path.toFile.delete()

  }

  // Returns a queue that receives the files via websocket and verifies that the data is correct (if doCompareFiles is true).
  def makeQueue(name: String, promise: Promise[ReceivedFile], log: LoggingAdapter, delay: FiniteDuration)(
      implicit mat: Materializer
  ): SourceQueueWithComplete[ReceivedFile] = {
    val startTime: Long = System.currentTimeMillis()
    println(s"$name: Started timing")
    Source
      .queue[ReceivedFile](1, OverflowStrategy.dropHead)
      .map(receiveFile(name, _, promise, startTime, delay))
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

  implicit class RichFuture[T](val f: Future[T]) extends AnyVal {
    def await(timeout: FiniteDuration): T = Await.result(f, timeout)
  }

  def actorSystemCreator(name: String)(config: Config): ActorSystem = {
    val host = InetAddress.getLocalHost.getHostAddress
//    val cfg = ConfigFactory.parseString(s"""
//            | akka.remote.netty.tcp.bind-hostname = $host
//            | akka.remote.artery.canonical.bind-hostname = $host
//            """).withFallback(config)
    val cfg = ConfigFactory.parseString(s"akka.remote.netty.tcp.bind-hostname=$host").withFallback(config)

    println(s"\nXXXXXXXXXXXX h=$host: \nhost = ${cfg.getString("akka.remote.netty.tcp.hostname")}, bind-host = ${cfg.getString("akka.remote.netty.tcp.bind-hostname")}\n\n")
    ActorSystem(name, cfg)
  }

}


class VbdsServerTest(name: String) extends MultiNodeSpec(VbdsServerTestConfig, VbdsServerTest.actorSystemCreator(name))
  with STMultiNodeSpec with ImplicitSender with BeforeAndAfterAll {

  import VbdsServerTestConfig._
  import VbdsServerTest._

  def initialParticipants = roles.size

  "A VbdsServerTest" must {

    "wait for all server nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "Allow creating a stream, subscribing and publishing to a stream" in {
      runOn(server1) {
        val host = system.settings.config.getString("multinode.host")
        val bindHost = InetAddress.getLocalHost.getHostAddress
        println(s"server1 (seed node) is running on $host")

        // Start the first server (the seed node)
        VbdsServerApp.main(
          Array(
                "--http-host", host,
                "--http-bind-host", bindHost,
                "--http-port", s"$server1HttpPort",
                "--akka-host", host,
                "--akka-bind-host", bindHost,
                "--akka-port", s"$seedPort",
                "-s", s"$host:$seedPort")
        )
        expectNoMessage(2.seconds)
        enterBarrier("deployed")
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        within(longTimeout) {
          enterBarrier("receivedFiles")
        }
      }

      runOn(server2) {
        val host       = system.settings.config.getString("multinode.host")
        val bindHost = InetAddress.getLocalHost.getHostAddress
        val serverHost = system.settings.config.getString("multinode.server-host")
        println(s"server2 is running on $host (seed node is $serverHost)")

        // Start a second server
        VbdsServerApp.main(
          Array(
            "--http-host", host,
            "--http-bind-host", bindHost,
            "--http-port", s"$server2HttpPort",
            "--akka-host", host,
            "--akka-bind-host", bindHost,
            "-s", s"$serverHost:$seedPort")
        )
        expectNoMessage(2.seconds)
        enterBarrier("deployed")
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        within(longTimeout) {
          enterBarrier("receivedFiles")
        }
      }

      runOn(subscriber1) {
        val host = system.settings.config.getString("multinode.host")
        println(s"subscriber1 is running on $host")
        implicit val materializer = ActorMaterializer()
        enterBarrier("deployed")
        val client = new VbdsClient("subscriber1", host, server1HttpPort)
        enterBarrier("streamCreated")
        val promise = Promise[ReceivedFile]
        val queue   = makeQueue("subscriber1", promise, log, subscriber1Delay)
        val subscribeResponse =
          client.subscribe(streamName, getTempDir("subscriber1"), queue, doCompareFiles).await(shortTimeout)
        assert(subscribeResponse.status == StatusCodes.SwitchingProtocols)
        enterBarrier("subscribedToStream")
        promise.future.await(longTimeout)
        within(longTimeout) {
          enterBarrier("receivedFiles")
        }
      }

      runOn(subscriber2) {
        val host = system.settings.config.getString("multinode.host")
        println(s"subscriber2 is running on $host")
        implicit val materializer = ActorMaterializer()
        enterBarrier("deployed")
        val client = new VbdsClient("subscriber2", host, server2HttpPort)
        enterBarrier("streamCreated")
        val promise = Promise[ReceivedFile]
        val queue   = makeQueue("subscriber2", promise, log, subscriber2Delay)
        val subscribeResponse =
          client.subscribe(streamName, getTempDir("subscriber2"), queue, doCompareFiles).await(shortTimeout)
        assert(subscribeResponse.status == StatusCodes.SwitchingProtocols)
        enterBarrier("subscribedToStream")
        promise.future.await(longTimeout)
        within(longTimeout) {
          enterBarrier("receivedFiles")
        }
      }

      runOn(publisher1) {
        val host = system.settings.config.getString("multinode.host")
        println(s"publisher1 is running on $host")
        implicit val materializer = ActorMaterializer()
        enterBarrier("deployed")
        val client         = new VbdsClient("publisher1", host, server1HttpPort)
        val createResponse = client.createStream(streamName).await(shortTimeout)
        assert(createResponse.status == StatusCodes.OK)
        enterBarrier("streamCreated")
        enterBarrier("subscribedToStream")
        Source(1 to numFilesToPublish).runForeach { _ =>
          client.publish(streamName, testFile, publisherDelay).await(shortTimeout)
        }
        within(longTimeout) {
          enterBarrier("receivedFiles")
        }
      }

      enterBarrier("finished")
    }
  }
}
