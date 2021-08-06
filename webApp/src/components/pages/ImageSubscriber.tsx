import React from 'react'
import {accessRoute, useAppContext} from "../../AppContext";
import {StreamSelector} from "../StreamSelector";
import {ImageConsumer} from "./ImageConsumer";
import {ServerSelector} from "../ServerSelector";
import {FitsImageConsumer} from "./FitsImageConsumer";

export const ImageSubscriber = (): JSX.Element => {
  const {serverInfo, selectedStream} = useAppContext()

  const webSocketUri = (serverInfo && selectedStream) ?
    `ws://${serverInfo.host}:${serverInfo.port}${accessRoute}/${selectedStream.name}`
    : undefined

  const isFits = selectedStream && selectedStream.contentType == "image/fits"

  if (webSocketUri) {
    if (isFits) {
      return (
        <div>
          <ServerSelector/>
          <StreamSelector/>
          <FitsImageConsumer webSocketUri={webSocketUri}/>
        </div>
      )
    }
    return (
      <div>
        <ServerSelector/>
        <StreamSelector/>
        <ImageConsumer webSocketUri={webSocketUri}/>
      </div>
    )
  }
  return (
    <div>
      <ServerSelector/>
      <StreamSelector/>
    </div>
  )
}
