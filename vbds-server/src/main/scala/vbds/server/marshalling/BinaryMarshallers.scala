package vbds.server.marshalling

import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.model.HttpEntity.Chunked
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko.stream.scaladsl.Source

trait BinaryMarshallers {
  implicit def byteStringMarshaller[T: BFormat]
    : Marshaller[Source[T, Any], Chunked] = Marshaller.opaque {
    (source: Source[T, Any]) =>
      val byteStrings = source.map(BFormat[T].write)
      HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`,
                                  byteStrings)
  }

  implicit def byteStringUnmarshaller[T: BFormat]
    : Unmarshaller[HttpEntity, Source[T, Any]] = Unmarshaller.strict {
    (entity: HttpEntity) =>
      entity.dataBytes.map(BFormat[T].read)
  }
}
