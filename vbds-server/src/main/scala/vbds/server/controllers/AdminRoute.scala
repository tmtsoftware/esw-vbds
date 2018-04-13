package vbds.server.controllers

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import vbds.server.actors.AdminData
import vbds.server.models.JsonSupport

/**
  * Provides the HTTP route for the VBDS Admin Service.
  * @param adminData used to access the distributed list of streams (using cluster + CRDT)
  */
class AdminRoute(adminData: AdminData) extends Directives with JsonSupport {

  val route =
    pathPrefix("vbds" / "admin" / "streams") {
      // List all streams: Response: OK: Stream names and descriptions in JSON; empty document if no streams
      get {
        onSuccess(adminData.listStreams()) { streams =>
          complete(streams)
        }
      } ~
        // Create a stream, Response: OK: Stream name and any other details returned as JSON, or 409: Conflict stream exists
        post {
          path(Remaining) { name =>
            onSuccess(adminData.streamExists(name)) { exists =>
              if (exists) {
                complete(Conflict -> s"The stream $name already exists")
              } else {
                onSuccess(adminData.addStream(name)) { info =>
                  complete(info)
                }
              }
            }
          }
        } ~
        // Deletes a stream: Reposnse: OK: Stream name and any other details returned as JSON, or 404: Stream not found
        delete {
          path(Remaining) { name =>
            onSuccess(adminData.streamExists(name)) { exists =>
              if (exists) {
                onSuccess(adminData.deleteStream(name)) { info =>
                  complete(info)
                }
              } else {
                complete(NotFound -> s"The stream $name does not exists")
              }
            }

          }
        }
    }
}
