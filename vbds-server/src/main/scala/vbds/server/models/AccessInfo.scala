package vbds.server.models

/**
  * Represents a subscriber to a stream of images
  *
  * @param streamName name of the stream
  * @param host subscriber's http host
  * @param port subscriber's http port
  * @param id unique ID for this subscription
  */
case class AccessInfo(streamName: String, host: String, port: Int, id: String)
