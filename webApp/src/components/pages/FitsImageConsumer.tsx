import React, {useEffect, useLayoutEffect, useRef, useState} from 'react'
import {useAppContext} from "../../AppContext"

type ImageConsumerProps = {
  webSocketUri: string,
}

export const FitsImageConsumer = ({webSocketUri}: ImageConsumerProps): JSX.Element => {
  const {selectedStream} = useAppContext()
  const messageHistory = useRef<Array<Uint8Array>>([])

  const ws = useRef<WebSocket | null>(null);
  useEffect(() => {
    // if (ws.current) {
    //   console.log('XXX closing websocket before open')
    //   ws.current.close()
    //   ws.current = null
    // }
    console.log('XXX new websocket ', webSocketUri)
    ws.current = new WebSocket(webSocketUri)
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
    return () => {
      if (ws.current) {
        console.log('XXX  closing websocket')
        ws.current.close()
        ws.current = null
        messageHistory.current = []
      }
    }
  }, [webSocketUri])

  // useEffect(() => {
  //   if (ws.current) {
  //     console.log('XXX  closing websocket (2)')
  //   }
  //
  // }, [selectedStream])

  // useEffect(() => {
  //   console.log('XXX init JS9')
  //   // @ts-ignore
  //   JS9.globalOpts.alerts = false
  //   // @ts-ignore
  //   JS9.imageOpts.inherit = true
  //   // @ts-ignore
  //   JS9.init()
  // }, [])

  // useLayoutEffect(() => {
  //   return () => {
  //     console.log('XXX unmount FitsImageConsumer')
  //     if (ws.current) {
  //       ws.current.close()
  //       ws.current = null
  //     }
  //   }
  // }, [])

  function onloadHandler(_: Event) {
    console.log('XXX FITS onloadHandler')
    if (ws.current) {
      if (ws.current.readyState == WebSocket.OPEN) {
        console.log('XXX FITS send ACK')
        ws.current.send('ACK')
      } else console.log('XXX readyState = ', ws.current.readyState)
    } else console.log('XXX websocket is undefined')
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
    console.log('XXX loading new image')
  }

  return (
    // <div id="centerdiv" style={{
    //   margin: '20px',
    // }}>
    //   <div className='JS9Menubar'/>
    //   <div className='JS9'/>
    //   <div style={{marginTop: '2px'}}>
    //     <div className='JS9Colorbar'/>
    //   </div>
    // </div>
    <></>
  )
}
