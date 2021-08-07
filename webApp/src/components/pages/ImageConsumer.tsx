import React, {useEffect, useRef} from 'react'
import {useAppContext} from "../../AppContext";

type ImageConsumerProps = {
  webSocketUri: string,
}

// Keeping this reference outside the component seems to work better (tried useRef, but had issues)
var imageConsumerWebSocket: WebSocket

export const ImageConsumer = ({webSocketUri}: ImageConsumerProps): JSX.Element => {
  const {selectedStream} = useAppContext()
  const messageHistory = useRef<Array<Uint8Array>>([])

  const img = new Image()

  // TODO
  const canvasWidth = 1920
  const canvasHeight = 1080

  const canvasRef = useRef<HTMLCanvasElement>(null)

  console.log('XXX new websocket ', webSocketUri)
  imageConsumerWebSocket = new WebSocket(webSocketUri);

  useEffect(() => {
    imageConsumerWebSocket.binaryType = 'arraybuffer'
    imageConsumerWebSocket.onmessage = (event) => {
      const arrayBuffer = new Uint8Array(event.data)
      if (arrayBuffer.byteLength == 1) {
        const buffers = messageHistory.current
        const blob = new Blob(buffers, {type: selectedStream?.contentType})
        displayImage(blob)
        messageHistory.current = []
      } else {
        messageHistory.current = messageHistory.current.concat(arrayBuffer)
      }
    }
  }, [webSocketUri])

  useEffect(() => () => {
    console.log('XXX  closing websocket')
    imageConsumerWebSocket.close()
    messageHistory.current = []
  }, [imageConsumerWebSocket])


  img.onload = () => {
    const ctx = canvasRef.current?.getContext("2d")
    if (ctx) {
      ctx.drawImage(img, 0, 0, img.naturalHeight, img.naturalWidth)
    }
    URL.revokeObjectURL(img.src)
    if (imageConsumerWebSocket.readyState == WebSocket.OPEN) {
      imageConsumerWebSocket.send('ACK')
    } else console.log('XXX readyState = ', imageConsumerWebSocket.readyState)
  }

  const urlCreator = window.URL || window.webkitURL;

  function displayImage(blob: Blob) {
    img.src = urlCreator.createObjectURL(blob)
  }

  return (
    <canvas
      ref={canvasRef}
      height={canvasHeight}
      width={canvasWidth}
      style={{border: '3px'}}/>
  )
}
