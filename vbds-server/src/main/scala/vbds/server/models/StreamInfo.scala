package vbds.server.models

import vbds.server.marshalling.VbdsSerializable

/**
  * Represents a data stream
 *
  * @param name name of the stream
  */
case class StreamInfo(name: String) extends VbdsSerializable
