package vbds.server.marshalling

import csw.commons.CborAkkaSerializer
import vbds.server.models.{AccessInfo, ServerInfo, StreamInfo}
import io.bullet.borer.Codec
import io.bullet.borer.derivation.CompactMapBasedCodecs.deriveCodec

class VbdsSerializer extends CborAkkaSerializer[VbdsSerializable] {
  override def identifier: Int = 19999
  implicit lazy val accessInfoCodec: Codec[AccessInfo] = deriveCodec
  implicit lazy val serverInfoCodec: Codec[ServerInfo] = deriveCodec
  implicit lazy val streamInfoCodec: Codec[StreamInfo] = deriveCodec

  register[AccessInfo]
  register[ServerInfo]
  register[StreamInfo]
}
