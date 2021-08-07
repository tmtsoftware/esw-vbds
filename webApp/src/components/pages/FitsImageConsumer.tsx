import React, {useEffect, useRef} from 'react'
import {useAppContext} from "../../AppContext"

type ImageConsumerProps = {
  webSocketUri: string,
}

// Keeping this reference outside the component seems to work better (tried useRef, but had issues)
var fitsImageConsumerWebSocket: WebSocket

export const FitsImageConsumer = ({webSocketUri}: ImageConsumerProps): JSX.Element => {
  const {selectedStream} = useAppContext()
  const messageHistory = useRef<Array<Uint8Array>>([])

  console.log('XXX new websocket ', webSocketUri)
  fitsImageConsumerWebSocket = new WebSocket(webSocketUri)

  useEffect(() => {
    fitsImageConsumerWebSocket.binaryType = 'arraybuffer'
    fitsImageConsumerWebSocket.onmessage = (event) => {
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
  }, [webSocketUri, selectedStream])

  useEffect(() => () => {
    console.log('XXX  closing websocket')
    sendAck()
    fitsImageConsumerWebSocket.close()
    messageHistory.current = []
  }, [fitsImageConsumerWebSocket])


  // XXX This is only needed if the js9 divs are managed in this component,
  // however that does not work well, since it is not a react component
  // useEffect(() => {
  //   // @ts-ignore
  //   console.log('XXX init JS9')
  //   // @ts-ignore
  //   JS9.globalOpts.alerts = false
  //   // @ts-ignore
  //   JS9.imageOpts.inherit = true
  //   // @ts-ignore
  //   JS9.init()
  // }, [])

  function sendAck() {
    if (fitsImageConsumerWebSocket.readyState == WebSocket.OPEN) {
      fitsImageConsumerWebSocket.send('ACK')
      console.log('XXX sent ACK')
    } else console.log('XXX Cannot send ACK: readyState = ', fitsImageConsumerWebSocket.readyState)
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

  return (
    // XXX This works better when done in index.html, since js9 is not a react component

    // <div className='resize both' id="centerdiv" style={{
    //   width: '1024px',
    //   height: '1024px',
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
