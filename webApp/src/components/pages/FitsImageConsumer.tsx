import React, {useEffect, useLayoutEffect, useRef, useState} from 'react'
import {useAppContext} from "../../AppContext"

type ImageConsumerProps = {
  webSocketUri: string,
}

export const FitsImageConsumer = ({webSocketUri}: ImageConsumerProps): JSX.Element => {
  const {selectedStream} = useAppContext()
  const messageHistory = useRef<Array<Uint8Array>>([])

  console.log('XXX new websocket ', webSocketUri)
  const ws = useRef<WebSocket>(new WebSocket(webSocketUri));

  useEffect(() => {
    ws.current.binaryType = 'arraybuffer'
    ws.current.onclose = () => {
      console.log('XXX ws onclose')
    }
    ws.current.onopen = () => {
      console.log('XXX ws onopen')
    }
    ws.current.onerror = () => {
      console.log('XXX ws onerror')
    }
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
  }, [webSocketUri])

  useEffect(() => () => {
    console.log('XXX  closing websocket')
    sendAck()
    ws.current.close()
    messageHistory.current = []
  }, [ws])


  useEffect(() => {
    console.log('XXX init JS9')
    // @ts-ignore
    JS9.globalOpts.alerts = false
    // @ts-ignore
    JS9.imageOpts.inherit = true
    // @ts-ignore
    JS9.init()
  }, [])

  function sendAck() {
    if (ws.current.readyState == WebSocket.OPEN) {
      ws.current.send('ACK')
      console.log('XXX sent ACK')
    } else console.log('XXX Cannot send ACK: readyState = ', ws.current.readyState)
  }

  function onloadHandler(_: Event) {
    console.log('XXX FITS onloadHandler')
    sendAck()
  }

  const settings = {
    onload: onloadHandler,
    filename: "vbds"
  }

  function displayImage(blob: Blob) {
    // @ts-ignore
    const savedSettings = JS9.GetParam("all")
    // @ts-ignore
    JS9.CloseImage({clear: false})
    // @ts-ignore
    JS9.Load(blob, {...settings, ...savedSettings})
    console.log('XXX loading new image', {...settings, ...savedSettings})
  }

  /*
        position: absolute;
        margin: auto;
        top: 100px;
        right: 0;
        bottom: 0;
        left: 20px;
        width: 512px;
        height: 512px;
   */

  return (
    <div className='resize both' id="centerdiv" style={{
      width: '1024px',
      height: '1024px',
      margin: '20px',
    }}>
      <div className='JS9Menubar'/>
      <div className='JS9'/>
      <div style={{marginTop: '2px'}}>
        <div className='JS9Colorbar'/>
      </div>
    </div>
  )
}
