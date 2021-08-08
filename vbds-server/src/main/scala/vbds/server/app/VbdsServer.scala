package vbds.server.app

import akka.{Done, actor}
import akka.actor.{AddressFromURIString, CoordinatedShutdown}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{SpawnProtocol, _}
import akka.cluster.typed.{Cluster, JoinSeedNodes}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.location.api.models.Connection.HttpConnection
import csw.location.api.models.{ComponentId, ComponentType, HttpRegistration, Metadata, NetworkType}
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.LoggingSystemFactory
import csw.prefix.models.{Prefix, Subsystem}
import vbds.server.actors.SharedDataActor
import vbds.server.actors.AkkaTypedExtension.UserActorFactory
import vbds.server.actors.SharedDataActor.StopSharedDataActor

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

object VbdsServer {

  /**
   * VBDS ActorSystem name
   */
  val clusterName = "vbds-system"

  // Gets the akka seed nodes config line
  private def getSeedNodes(clusterSeeds: String): List[String] = {
    if (clusterSeeds.isEmpty)
      throw new IllegalArgumentException("Missing required seed nodes")
    clusterSeeds
      .split(",")
      .toList
      .map(s => s"akka://$clusterName@$s")
  }

  /**
   * Starts the server
   *
   * @param host         Akka and HTTP server bind host
   * @param httpPort     HTTP server port (must be > 0)
   * @param akkaPort     akka ActorSystem port (must be > 0)
   * @param name         the name of this server (for LocationService and ActorSystem)
   * @param clusterSeeds list of cluster seeds in the form host:port,host:port,... (Required even for seed node)
   * @return the root actor system
   */
  def start(
      host: String,
      httpPort: Int,
      akkaPort: Int,
      name: String,
      clusterSeeds: String
  ): ActorSystem[SpawnProtocol.Command] = {
    checkPort(httpPort)
    checkPort(akkaPort)

    // Generate the akka config for the akka and http ports as well as the cluster seed nodes
    val config = ConfigFactory.parseString(s"""
               akka.remote.artery.canonical.hostname=$host
               akka.remote.artery.canonical.port=$akkaPort
            """).withFallback(ConfigFactory.load())

    implicit val system = ActorSystem(SpawnProtocol(), clusterName, config)
    LoggingSystemFactory.start(BuildInfo.name, BuildInfo.version, host, system)
    val seedNodes       = getSeedNodes(clusterSeeds).map(AddressFromURIString.parse)
    val cluster         = Cluster(system)
    cluster.manager ! JoinSeedNodes(seedNodes)

    val actorRef = system.spawn(SharedDataActor(host, httpPort), name)
    registerWithLocationService(httpPort, name, actorRef)
    system
  }

  private def checkPort(port: Int): Unit = {
    if (port <= 0) throw new IllegalArgumentException(s"Invalid port number: $port")
  }

  // Registers this service with the Location Service
  private def registerWithLocationService(
      httpPort: Int,
      name: String,
      actorRef: ActorRef[SharedDataActor.SharedDataActorMessages]
  )(
      implicit system: ActorSystem[SpawnProtocol.Command]
  ): Unit = {
    val locationService = HttpLocationServiceFactory.makeLocalClient
    val connection = HttpConnection(
      ComponentId(
        // XXX TODO: Which Subsystem?
        Prefix(Subsystem.ESW, name),
        ComponentType.Service
      )
    )
    val timeout = 3.seconds
    if (Await.result(locationService.find(connection), timeout).nonEmpty) {
      Await.result(locationService.unregister(connection), timeout)
    }
    val registrationResult = Await.result(
      locationService.register(
        new HttpRegistration(
          connection,
          httpPort,
          "/",
          // XXX TODO: Inside or outside?
          NetworkType.Inside,
          Metadata.empty
        )
      ),
      timeout
    )

    // Unregister when shutting down ActorSystem
    val classicSystem: actor.ActorSystem         = system.toClassic
    val coordinatedShutdown: CoordinatedShutdown = CoordinatedShutdown(classicSystem)
    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      s"unregistering-${registrationResult.location}"
    )(() => {
      import system.*
      println("XXX coordinatedShutdown")
      val f = Future
        .sequence(
          List(
            registrationResult.unregister(),
            actorRef.ask(StopSharedDataActor)(Timeout(3.seconds), system.scheduler)
          )
        )
        .map(_ => Done)
      Await.ready(f, 5.seconds)
      f
    })

    // Unregister when the app exits
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        println("XXX shutdown hook")
        system.terminate()
      }
    })
  }
}
