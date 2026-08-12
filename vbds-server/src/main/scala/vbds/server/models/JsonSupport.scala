package vbds.server.models

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*

/**
  * Defines JSON I/O for model objects
  */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val streamInfoFormat: RootJsonFormat[StreamInfo] = jsonFormat2(StreamInfo.apply)
  implicit val accessInfoFormat: RootJsonFormat[AccessInfo] = jsonFormat4(AccessInfo.apply)

  // spray-json's CollectionFormats.setFormat targets scala.collection.Set, which is
  // incomparable to immutable.Iterable (the source of the Scala-3 ambiguity). A format
  // typed to immutable Set is strictly the most specific candidate, so it wins resolution.
  implicit def immutableSetFormat[T: JsonFormat]: RootJsonFormat[Set[T]] = new RootJsonFormat[Set[T]] {
    def write(set: Set[T]): JsValue = JsArray(set.map(_.toJson).toVector)
    def read(value: JsValue): Set[T] = value match {
      case JsArray(elements) => elements.map(_.convertTo[T]).toSet
      case x                 => deserializationError(s"Expected a Set as JsArray, but got $x")
    }
  }
}