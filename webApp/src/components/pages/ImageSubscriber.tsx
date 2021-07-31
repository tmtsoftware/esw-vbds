import React from 'react'
import {accessRoute, useAppContext} from "../../AppContext";
import {StreamSelector} from "../StreamSelector";
import {ImageConsumer} from "./ImageConsumer";

export const ImageSubscriber = (): JSX.Element => {
  const {serverInfo, selectedStream} = useAppContext()

  const webSocketUri = (serverInfo && selectedStream) ?
    `ws://${serverInfo.host}:${serverInfo.port}${accessRoute}/${selectedStream.name}`
    : undefined

  return <div>
    <StreamSelector/>
    {webSocketUri ? <ImageConsumer webSocketUri={webSocketUri}/> : <></>}
  </div>
}
