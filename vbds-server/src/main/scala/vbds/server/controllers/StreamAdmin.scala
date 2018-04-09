package vbds.server.controllers

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import vbds.server.models.{JsonSupport, StreamInfo}

object StreamAdmin extends Directives with JsonSupport {

  private var streams: Set[StreamInfo] = Set.empty

  private def streamExists(name: String): Boolean =
    streams.exists(_.name == name)

  private def addStream(name: String): Unit =
    streams = streams + StreamInfo(name)

  private def deleteStream(name: String): Unit =
    streams = streams.filter(_.name != name)

  val route =
    pathPrefix("vbds" / "admin" / "streams") {
      // List all streams: Response: OK: Stream names and descriptions in JSON; empty document if no streams
      get {
        complete(streams)
      } ~
        // Create a stream, Response: OK: Stream name and any other details returned as JSON, or 409: Conflict stream exists
        post {
          path(Remaining) { name =>
            if (streamExists(name)) {
              complete(Conflict -> s"The stream $name already exists")
            } else {
              addStream(name)
              complete(StreamInfo(name))
            }
          }
        } ~
        // Deletes a stream: Reposnse: OK: Stream name and any other details returned as JSON, or 404: Stream not found
        delete {
          path(Remaining) { name =>
            if (streamExists(name)) {
              deleteStream(name)
              complete(StreamInfo(name))
            } else {
              complete(NotFound -> s"The stream $name does not exists")
            }
          }
        }
    }
}
