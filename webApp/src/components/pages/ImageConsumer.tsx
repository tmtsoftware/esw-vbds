import React, {useEffect, useMemo, useRef, useState} from 'react'
import {useAppContext} from "../../AppContext";
import useWebSocket from "react-use-websocket";

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

  const ctx = canvasRef.current?.getContext("2d")

  console.log('XXX! making new websocket')
  const {
    sendMessage,
    lastMessage,
    readyState,
    getWebSocket
  } = useWebSocket(webSocketUri, {
    onOpen: () => {
      console.log('Opened websocket for stream', selectedStream?.name)
      // setCurrentWebSocket(getWebSocket())
    },
    onError: event => console.log(`Error for stream ${selectedStream?.name} websocket: `, event),
    onClose: event => {
      console.log(`Websocket closed for stream ${selectedStream?.name}: `, event)
      // setCurrentWebSocket(getWebSocket())
    },
    onMessage: event => {
      const arrayBuffer = new Uint8Array(event.data)
      console.log(`XXX onMessage: received for stream ${selectedStream?.name}: size: ${arrayBuffer.byteLength}`)
    },
    shouldReconnect: () => false,
  })

  const ws = getWebSocket()
  // setCurrentWebSocket(getWebSocket())
  if (ws) {
    (ws as WebSocket).binaryType = 'arraybuffer'
  }

  useEffect(() => {
    if (lastMessage) {
      const arrayBuffer = new Uint8Array(lastMessage.data)
      if (arrayBuffer.byteLength == 1) {
        const buffers = messageHistory.current
        console.log(`XXX Create Blob with type ${selectedStream?.contentType}`)
        const blob = new Blob(buffers, {type: selectedStream?.contentType})
        displayImage(blob)
        messageHistory.current = []
      } else {
        console.log(`XXX got an image chunk message on length ${arrayBuffer.byteLength}`)
        messageHistory.current = messageHistory.current.concat(arrayBuffer)
      }
    } else {
      console.log('XXX got some other message')
    }

  }, [lastMessage]);


  // messageHistory.current = useMemo<Array<Uint8Array>>(() => {
  //   if (lastMessage) {
  //     const arrayBuffer = new Uint8Array(lastMessage.data)
  //     if (arrayBuffer.byteLength == 1) {
  //       const buffers = messageHistory.current
  //       console.log(`XXX Create Blob with type ${selectedStream?.contentType}`)
  //       const blob = new Blob(buffers, {type: selectedStream?.contentType})
  //       displayImage(blob)
  //       return []
  //     } else {
  //       console.log(`XXX got an image chunk message on length ${arrayBuffer.byteLength}`)
  //       return messageHistory.current.concat(arrayBuffer)
  //     }
  //   } else {
  //     console.log('XXX got some other message')
  //     return messageHistory.current
  //   }
  // }, [lastMessage])


  console.log("XXX! Calling img.onload")
  img.onload = () => {
    console.log("XXX Loaded canvas image")
    console.log(`XXX ctx.drawImage ${img.naturalHeight}, ${img.naturalWidth}`)
    ctx!.drawImage(img, 0, 0, img.naturalHeight, img.naturalWidth)
    URL.revokeObjectURL(img.src)
    sendMessage('ACK')
  }

  function displayImage(blob: Blob) {
    console.log(`XXX displayImage: ctx = ${ctx}, blob = `, blob)
    const urlCreator = window.URL || window.webkitURL;
    const x = urlCreator.createObjectURL(blob)
    console.log(`XXX x = ${x}`)
    img.src = x
  }

  return canvas
}
