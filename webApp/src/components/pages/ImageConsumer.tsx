import React, {useMemo, useRef, useState} from 'react'
import {useAppContext} from "../../AppContext";
import useWebSocket from "react-use-websocket";
import {ImageDisplay} from "./ImageDisplay";

type ImageConsumerProps = {
  webSocketUri: string,
}

export const ImageConsumer = ({webSocketUri}: ImageConsumerProps): JSX.Element => {
  const {selectedStream, setCurrentWebSocket} = useAppContext()
  const messageHistory = useRef<Array<Uint8Array>>([])
  const [blob, setBlob] = useState<Blob | undefined>(undefined)

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
  if (ws) {
    (ws as WebSocket).binaryType = 'arraybuffer'
    setCurrentWebSocket(getWebSocket())
  }

  messageHistory.current = useMemo<Array<Uint8Array>>(() => {
    if (lastMessage) {
      const arrayBuffer = new Uint8Array(lastMessage.data)
      if (arrayBuffer.byteLength == 1) {
        const buffers = messageHistory.current
        console.log(`XXX Create Blob with type ${selectedStream?.contentType}`)
        setBlob(new Blob(buffers, {type: selectedStream?.contentType}))
        return []
      } else {
        console.log(`XXX got an image chunk message on length ${arrayBuffer.byteLength}`)
        return messageHistory.current.concat(arrayBuffer)
      }
    } else {
      console.log('XXX got some other message')
      return messageHistory.current
    }
  }, [lastMessage])

  return blob ? <ImageDisplay ack={() => sendMessage('ACK')} blob={blob}/> : <></>
}
