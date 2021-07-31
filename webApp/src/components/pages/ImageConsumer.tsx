import React, {useEffect, useRef} from 'react'
import {useAppContext} from "../../AppContext";

type ImageConsumerProps = {
  webSocketUri: string,
}

export const ImageConsumer = ({webSocketUri}: ImageConsumerProps): JSX.Element => {
  const {selectedStream} = useAppContext()
  const messageHistory = useRef<Array<Uint8Array>>([])

  const img = new Image()

  // TODO
  const canvasWidth = 1920
  const canvasHeight = 1080

  const canvasRef = useRef<HTMLCanvasElement>(null)
  const canvas = <canvas
    ref={canvasRef}
    height={canvasHeight}
    width={canvasWidth}
    style={{border: '3px'}}/>

  const ws = useRef<WebSocket | null>(null);
  useEffect(() => {
    if (ws.current) {
      ws.current.close()
      ws.current = null
    }
    ws.current = new WebSocket(webSocketUri)
    ws.current.binaryType = 'arraybuffer'
    ws.current.onmessage = (event) => {
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

    return () => {
      if (ws.current) ws.current.close();
    };
  }, [webSocketUri]);


  img.onload = () => {
    const ctx = canvasRef.current?.getContext("2d")
    if (ctx) {
      ctx.drawImage(img, 0, 0, img.naturalHeight, img.naturalWidth)
    }
    URL.revokeObjectURL(img.src)
    if (ws.current && ws.current.readyState == WebSocket.OPEN) {
      ws.current.send('ACK')
    }
  }

  const urlCreator = window.URL || window.webkitURL;

  function displayImage(blob: Blob) {
    img.src = urlCreator.createObjectURL(blob)
  }

  return canvas
}
