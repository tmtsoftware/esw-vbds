package vbds.server.routes

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.event.{LogSource, Logging}

/**
 * Logging support for routes
 */
trait LoggingSupport {
  val actorSystem: ActorSystem[SpawnProtocol.Command]

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[?] = o.getClass
  }

  val log = Logging(actorSystem.classicSystem, this)
}
