package vbds.server.app

import akka.actor.typed._
import com.typesafe.config.ConfigFactory
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
   * @param httpHost     HTTP server bind host
   * @param httpPort     HTTP server port (0 for random)
   * @param akkaHost     akka ActorSystem bind host
   * @param akkaPort     akka ActorSystem port (0 for random)
   * @param clusterSeeds list of cluster seeds in the form host:port,host:port,... (Required even for seed node)
   * @return the root actor system
   */
  def start(httpHost: String,
            httpPort: Int,
            akkaHost: String,
            akkaPort: Int,
            clusterSeeds: String): ActorSystem[SpawnProtocol.Command] = {

    val seedNodes = getSeedNodes(clusterSeeds)

    // Generate the akka config for the akka and http ports as well as the cluster seed nodes
    val config = ConfigFactory.parseString(s"""
               akka.remote.netty.tcp.hostname=$akkaHost
               akka.remote.netty.tcp.port=$akkaPort
               akka.remote.artery.canonical.hostname=$akkaHost
               akka.remote.artery.canonical.port=$akkaPort
               $seedNodes
            """).withFallback(ConfigFactory.load())

    implicit val system = ActorSystem(SpawnProtocol(), clusterName, config)
    system.spawn(SharedDataActor(httpHost, httpPort), "SharedDataActor")
    system
  }
}
