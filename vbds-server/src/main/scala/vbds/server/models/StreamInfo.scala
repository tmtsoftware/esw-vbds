package vbds.server.models

import vbds.server.marshalling.VbdsSerializable

/**
  * Represents an image stream
 *
  * @param name name of the stream
  */
case class StreamInfo(name: String) extends VbdsSerializable
