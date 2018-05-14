package vbds.server.models

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Defines JSON I/O for model objects
  */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val streamInfoFormat = jsonFormat1(StreamInfo)
  implicit val accessInfoFormat = jsonFormat4(AccessInfo)
}
