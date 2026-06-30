package vbds.server.models

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/**
  * Defines JSON I/O for model objects
  */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val streamInfoFormat: RootJsonFormat[StreamInfo] = jsonFormat2(StreamInfo)
  implicit val accessInfoFormat: RootJsonFormat[AccessInfo] = jsonFormat4(AccessInfo)
}
