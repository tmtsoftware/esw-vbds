import React, {useEffect, useState} from 'react'
import {Select, Typography} from "antd";
import {accessRoute, adminRoute, useAppContext} from "../AppContext";
import {get} from '../utils/Http'
import type {StreamInfo} from "../models/Models";

const {Option} = Select;

export const StreamSelector = (): JSX.Element => {

  const {serverInfo, availableStreams, setAvailableStreams, selectedStream, setSelectedStream} = useAppContext()

  const {Text} = Typography;

  var ws: WebSocket = undefined

  function changeStream(stream: string | undefined) {
    console.log('XXX Selected stream is ', stream)
    setSelectedStream(availableStreams.find(s => s.name == stream))
    if (stream && serverInfo) {
      const uri = `ws://${serverInfo.host}:${serverInfo.port}${accessRoute}/${stream}`
      if (ws) {
        ws.close()
      }
      ws = new WebSocket(uri)
      if (ws) {
        ws.binaryType = 'arraybuffer'
        ws.onopen = () => {
          console.log('Opened websocket for stream', stream)
        }
        ws.onerror = event => {
          console.log('Error for stream $stream websocket: ', event)
        }
        // ws.onmessage = event => {
        //   const arrayBuffer = new Uint8Array(event.data)
        //   console.log(`XXX received message with ${arrayBuffer.byteLength} bytes`)
        //   // End marker is a message with one byte ("\n")
        //   if (arrayBuffer.byteLength == 1) {
        //     const buffers = currentImageData.reverse
        //     setCurrentImageData(undefined)
        //     // JS9 has code to "flatten if necessary", so we can just pass in all the file parts together
        //     const blob = new Blob(js.Array(buffers: _*), getImageProps)
        //     displayImage(blob)
        //   } else {
        //     currentImageData = new Uint8Array(arrayBuffer) :: currentImageData
        //   }
        // }
      }
      /*
      ws.onmessage = { event: MessageEvent =>
        val arrayBuffer = new Uint8Array(event.data.asInstanceOf[ArrayBuffer])
        println(s"XXX received message with ${arrayBuffer.byteLength} bytes")
        // End marker is a message with one byte ("\n")
        if (arrayBuffer.byteLength == 1) {
          val buffers = currentImageData.reverse
          currentImageData = Nil
          // JS9 has code to "flatten if necessary", so we can just pass in all the file parts together
          val blob = new Blob(js.Array(buffers: _*), getImageProps)
          displayImage(blob)
        } else {
          currentImageData = new Uint8Array(arrayBuffer) :: currentImageData
        }
      }
      ws.onclose = { _: Event =>
        println(s"Websocket closed for stream $stream")
      }

       */
    }

  }

  useEffect(() => {
    console.log(`XXX StreamSelector: serverInfo changed: ${serverInfo?.host}:${serverInfo?.port}`)
    const uri = serverInfo ? `http://${serverInfo.host}:${serverInfo.port}${adminRoute}` : undefined
    if (uri) {
      get<Array<StreamInfo>>(uri).then((resp) => {
        const result = resp.parsedBody
        setAvailableStreams(result ? result : [])
      })
    } else setAvailableStreams([])
  }, [serverInfo])


  function streamsSelector(): JSX.Element {
    const defaultOption =
      <Option key={'None'} value={'None'}>
        {'None'}
      </Option>

    const options = Array.of(defaultOption).concat(availableStreams.map((stream) => {
      return (
        <Option key={stream.name} value={stream.name}>
          {`${stream.name} [${stream.contentType}]`}
        </Option>
      )
    }))

    return (
      <div style={{margin: 10, padding: 10}}>
        <Text>Stream:</Text>
        <Select
          style={{marginLeft: 10, minWidth: '60ch'}}
          placeholder="Select an image stream"
          value={selectedStream ? selectedStream.name : 'None'}
          onChange={changeStream}
          disabled={false}
        >
          {options}
        </Select>
      </div>
    )
  }

  return streamsSelector()
}
