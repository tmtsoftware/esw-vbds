package vbds.server.routes

import akka.http.scaladsl.server.Directives
import vbds.server.twirl.Implicits._

class WebRoute() extends Directives {
  val route = {
    pathSingleSlash {
      get {
        complete {
          vbds.server.ui.html.index.render()
        }
      }
    } ~
    pathPrefix("assets" / Remaining) { file =>
      encodeResponse {
        getFromResource("public/" + file)
      }
    }
  }
}
