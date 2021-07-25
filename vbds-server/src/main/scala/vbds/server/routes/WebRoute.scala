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
    pathPrefix("js9" / Remaining) { file =>
      encodeResponse {
        getFromResource("js9/" + file)
      }
    }
  }
}
