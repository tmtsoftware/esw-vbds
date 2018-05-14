package vbds.server.models

import vbds.server.marshalling.VbdsSerializable

/**
  * Represents a subscriber to a stream of data files
  *
  * @param streamName name of the stream
  * @param host subscriber's http host
  * @param port subscriber's http port
  * @param id unique ID for this subscription
  */
case class AccessInfo(streamName: String, host: String, port: Int, id: String) extends VbdsSerializable

/**
  * Represents a remote HTTP server that has subscribers to data files
  */
case class ServerInfo(host: String, port: Int) extends VbdsSerializable
