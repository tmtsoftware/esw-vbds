package vbds.server.app

import java.net.InetSocketAddress

import akka.actor.typed._
import akka.cluster.typed._
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteConcatenation._
import com.typesafe.config.ConfigFactory
import vbds.server.actors.SharedDataActor.{LocalAddress, SharedDataActorMessages}
import vbds.server.actors.{AccessApiImpl, AdminApiImpl, SharedDataActor, TransferApiImpl}
import vbds.server.routes.{AccessRoute, AdminRoute, TransferRoute}

import scala.concurrent.{ExecutionContext, Future}

object VbdsServer {

  /**
   * VBDS ActorSystem name
   */
  val systemName = "vbds-system"

  // Gets the akka seed nodes config line
  private def getSeedNodes(clusterSeeds: String): String = {
    if (clusterSeeds.nonEmpty) {
      val seeds = clusterSeeds
        .split(",")
        .map(s => s""""akka.tcp://$systemName@$s"""")
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
            clusterSeeds: String): ActorSystem[SharedDataActorMessages] = {

    val seedNodes = getSeedNodes(clusterSeeds)

    // Generate the akka config for the akka and http ports as well as the cluster seed nodes
    val config = ConfigFactory.parseString(s"""
               akka.remote.netty.tcp.hostname=$akkaHost
               akka.remote.netty.tcp.port=$akkaPort
               akka.remote.artery.canonical.hostname=$akkaHost
               akka.remote.artery.canonical.port=$akkaPort
               $seedNodes
            """).withFallback(ConfigFactory.load())

    ActorSystem(SharedDataActor(httpHost, httpPort), systemName, config)
  }
}

///**
// * Top level class for the VIZ Bulk Data System (VBDS).
// *
// * @param sharedDataActor the cluster actor that shares data on streams and subscribers
// */
//class VbdsServer(sharedDataActor: ActorRef[SharedDataActorMessages]) {
//
//  implicit val ec: ExecutionContext = system.executionContext
//
//  private val adminApi    = new AdminApiImpl(sharedDataActor)
//  private val accessApi   = new AccessApiImpl(sharedDataActor)
//  private val transferApi = new TransferApiImpl(sharedDataActor, accessApi)
//
//  private val adminRoute    = new AdminRoute(adminApi)
//  private val accessRoute   = new AccessRoute(adminApi, accessApi)
//  private val transferRoute = new TransferRoute(adminApi, accessApi, transferApi)
//  private val route         = adminRoute.route ~ accessRoute.route ~ transferRoute.route

//  /**
//   * Starts the server on the given host and port
//   *
//   * @return the future server binding
//   */
//  private def start(host: String, port: Int): Future[Http.ServerBinding] =
//    Http().bindAndHandle(route, host, port)

//  /**
//   * Stops the server
//   *
//   * @param binding the return value from start()
//   */
//  def stop(binding: Http.ServerBinding): Unit =
//    binding.unbind().onComplete(_ => system.terminate())
//
//}
