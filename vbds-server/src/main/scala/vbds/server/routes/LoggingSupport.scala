package vbds.server.routes

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.event.{LogSource, Logging}

/**
 * Logging support for routes
 */
trait LoggingSupport {
  val actorSystem: ActorSystem[SpawnProtocol.Command]

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  val log = Logging(actorSystem.classicSystem, this)
}
