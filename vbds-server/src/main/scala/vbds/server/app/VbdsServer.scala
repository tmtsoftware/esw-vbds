package vbds.server.app

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteConcatenation._
import akka.stream.ActorMaterializer
import vbds.server.actors.SharedDataActor.LocalAddress
import vbds.server.actors.{AccessApiImpl, AdminApiImpl, SharedDataActor, TransferApiImpl}
import vbds.server.routes.{AccessRoute, AdminRoute, TransferRoute}

import scala.concurrent.Future

object VbdsServer {
  /**
    * Starts the server (Assumes that the given ActorSystem is already configured correctly for the cluster)
    */
  def start(httpHost: String, httpBindHost: String, httpPort: Int)(implicit system: ActorSystem): Future[Http.ServerBinding] = {
    implicit val mat = ActorMaterializer()

    // Initialize the cluster for replicating the data
    val replicator      = DistributedData(system).replicator
    implicit val node   = Cluster(system)
    val sharedDataActor = system.actorOf(SharedDataActor.props(replicator))
    new VbdsServer(sharedDataActor).start(httpHost, httpBindHost, httpPort)
  }
}

/**
  * Top level class for the VIZ Bulk Data System (VBDS).
  *
  * @param sharedDataActor the cluster actor that shares data on streams and subscribers
  */
private class VbdsServer(sharedDataActor: ActorRef)(implicit system: ActorSystem, mat: ActorMaterializer) {

  import system.dispatcher

  val adminApi    = new AdminApiImpl(sharedDataActor)
  val accessApi   = new AccessApiImpl(sharedDataActor)
  val transferApi = new TransferApiImpl(sharedDataActor, accessApi)

  val adminRoute    = new AdminRoute(adminApi)
  val accessRoute   = new AccessRoute(adminApi, accessApi)
  val transferRoute = new TransferRoute(adminApi, accessApi, transferApi)
  val route         = adminRoute.route ~ accessRoute.route ~ transferRoute.route

  /**
   * Starts the server on the given host and port
   *
   * @return the server binding
   */
  def start(host: String, httpBindHost: String, port: Int): Future[Http.ServerBinding] = {
    println(s"XXX Http().bindAndHandle($httpBindHost)")
    val f = Http().bindAndHandle(route, httpBindHost, port)
    val addr =
    // Need to know this http server's address when subscribing
    f.foreach(binding => sharedDataActor ! LocalAddress(new InetSocketAddress(host, binding.localAddress.getPort)))
    f
  }

  /**
   * Stops the server
   *
   * @param binding the return value from start()
   */
  def stop(binding: Http.ServerBinding): Unit =
    binding.unbind().onComplete(_ => system.terminate())

}
