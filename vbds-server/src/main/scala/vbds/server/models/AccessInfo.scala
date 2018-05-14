package vbds.server.models

import vbds.server.marshalling.VbdsSerializable

/**
  * Represents a subscriber to a stream of data files.
  * The host and port are used to send data from one HTTP server to another,
  * where it is distributed to the local subscribers.
  *
  * @param streamName name of the stream
  * @param host the host for the HTTP server where the subscriber subscribed to the stream
  * @param port the port for the HTTP server where the subscriber subscribed to the stream
  * @param id unique ID for this subscription
  */
case class AccessInfo(streamName: String, host: String, port: Int, id: String) extends VbdsSerializable

/**
  * Represents a remote HTTP server that has subscribers to data files
  */
case class ServerInfo(host: String, port: Int) extends VbdsSerializable
