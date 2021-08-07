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

  // Hide js9 if not displaying FITS
  const js9div = document.getElementById("centerdiv")
  if (js9div) js9div.style.display = isFits ? 'block' : 'none'

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
