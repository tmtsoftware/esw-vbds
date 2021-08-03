package vbds.server.app

import akka.actor.typed.{SpawnProtocol, _}
import com.typesafe.config.ConfigFactory
import csw.location.api.models.Connection.HttpConnection
import csw.location.api.models.{ComponentId, ComponentType, HttpRegistration, Metadata, NetworkType}
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.prefix.models.{Prefix, Subsystem}
import vbds.server.actors.SharedDataActor
import vbds.server.actors.AkkaTypedExtension.UserActorFactory

object VbdsServer {

  /**
   * VBDS ActorSystem name
   */
  val clusterName = "vbds-system"

  // Gets the akka seed nodes config line
  private def getSeedNodes(clusterSeeds: String): String = {
    if (clusterSeeds.nonEmpty) {
      val seeds = clusterSeeds
        .split(",")
        .map(s => s""""akka://$clusterName@$s"""")
        .mkString(",")
      s"akka.cluster.seed-nodes=[$seeds]"
    } else throw new IllegalArgumentException("Missing required seed nodes")
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
    val seedNodes = getSeedNodes(clusterSeeds)

    // Generate the akka config for the akka and http ports as well as the cluster seed nodes
    val config = ConfigFactory.parseString(s"""
               akka.remote.netty.tcp.hostname=$host
               akka.remote.netty.tcp.port=$akkaPort
               akka.remote.artery.canonical.hostname=$host
               akka.remote.artery.canonical.port=$akkaPort
               $seedNodes
            """).withFallback(ConfigFactory.load())

    implicit val system = ActorSystem(SpawnProtocol(), clusterName, config)
    system.spawn(SharedDataActor(host, httpPort), name)
    registerWithLocationService(httpPort, name)
    system
  }

  private def checkPort(port: Int): Unit = {
    if (port <= 0) throw new IllegalArgumentException(s"Invalid port number: $port")
  }

  // Registers this service with the Location Service
  private def registerWithLocationService(httpPort: Int, name: String)
                                         (implicit system: ActorSystem[SpawnProtocol.Command]): Unit = {
    val locationService = HttpLocationServiceFactory.makeLocalClient
    locationService.register(
      new HttpRegistration(
        HttpConnection(
          ComponentId(
            // XXX TODO: Which Subsystem?
            Prefix(Subsystem.ESW, name),
            ComponentType.Service
          )
        ),
        httpPort,
        "/",
        // XXX TODO: Inside or outside?
        NetworkType.Inside,
        Metadata.empty
      )
    )
  }
}
