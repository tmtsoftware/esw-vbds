import React, {useEffect, useRef, useState} from 'react'
import {accessRoute, useAppContext} from "../../AppContext";
import {StreamSelector} from "../StreamSelector";
import {ImageConsumer} from "./ImageConsumer";
import {ServerSelector} from "../ServerSelector";
import {FitsImageConsumer} from "./FitsImageConsumer";

// Keeping this reference outside the component seems to work better (tried useRef, but had issues)
let webSocket: WebSocket | undefined = undefined;

// Handles subscribing to an image stream and manages the websocket and passing
// the images to the image display components
export const ImageSubscriber = (): JSX.Element => {
  const {serverInfo, selectedStream} = useAppContext()
  const messageHistory = useRef<Array<Uint8Array>>([])
  const [blob, setBlob] = useState<Blob | undefined>(undefined)

  const isFits = selectedStream && selectedStream.contentType == "image/fits"

  // Hide js9 if not displaying FITS
  const js9div = document.getElementById("centerdiv")
  if (js9div) js9div.style.display = isFits ? 'block' : 'none'

  useEffect(() => {
    if (webSocket) {
      // close any previous websocket
      webSocket.close()
      webSocket = undefined
      messageHistory.current = []
    }
    const webSocketUri = (serverInfo && selectedStream) ?
      `ws://${serverInfo.host}:${serverInfo.port}${accessRoute}/${selectedStream.name}`
      : undefined
    webSocket = webSocketUri ? new WebSocket(webSocketUri) : undefined
    if (webSocket) {
      webSocket.binaryType = 'arraybuffer'
      webSocket.onmessage = (event) => {
        // images come in chunks, terminated by a message of length 1,
        // so we need to combine the chunks into a Blob before displaying.
        const arrayBuffer = new Uint8Array(event.data)
        if (arrayBuffer.byteLength == 1) {
          const buffers = messageHistory.current
          const blob = new Blob(buffers, {type: selectedStream?.contentType})
          setBlob(blob)
          messageHistory.current = []
        } else {
          messageHistory.current = messageHistory.current.concat(arrayBuffer)
        }
      }
    }
  }, [serverInfo, selectedStream])

  // Close the websocket when this component is unmounted
  useEffect(() => () => {
    if (webSocket) {
      sendAck()
      webSocket.close()
      webSocket = undefined
      messageHistory.current = []
    }
  }, [serverInfo, selectedStream])

  // Need to call this after receiving and displaying each complete image
  function sendAck() {
    if (webSocket) {
      if (webSocket.readyState == WebSocket.OPEN) {
        webSocket.send('ACK')
      }
    }
  }

  // Display js9 for FITS files, otherwise use a canvas based component
  if (webSocket) {
    if (isFits) {
      return (
        <div>
          <ServerSelector/>
          <StreamSelector/>
          <FitsImageConsumer blob={blob} sendAck={sendAck}/>
        </div>
      )
    }
    return (
      <div>
        <ServerSelector/>
        <StreamSelector/>
        <ImageConsumer blob={blob} sendAck={sendAck}/>
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
