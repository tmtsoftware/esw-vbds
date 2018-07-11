package vbds.server.models

import vbds.server.marshalling.VbdsSerializable

/**
  * Represents a data stream
 *
  * @param name name of the stream
  * @param contentType the type of files in the stream (Using String for simpler JSON conversion)
  */
case class StreamInfo(name: String, contentType: String) extends VbdsSerializable
