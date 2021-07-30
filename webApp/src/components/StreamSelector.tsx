import React, {useEffect, useMemo} from 'react'
import {Select, Typography} from "antd";
import {accessRoute, adminRoute, useAppContext} from "../AppContext";
import {get} from '../utils/Http'
import type {StreamInfo} from "../models/Models";
import {useSocketIO} from "react-use-websocket";

const {Option} = Select;

export const StreamSelector = (): JSX.Element => {

  const {serverInfo, availableStreams, setAvailableStreams, selectedStream, setSelectedStream, currentWebSocket} = useAppContext()

  const {Text} = Typography;

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

  function streamSelected(stream: string | undefined) {
    console.log('XXX Selected stream is ', stream)
    // if (currentWebSocket) currentWebSocket.close()
    setSelectedStream(availableStreams.find(s => s.name == stream))
  }

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
          onChange={streamSelected}
          disabled={false}
        >
          {options}
        </Select>
      </div>
    )
  }

  return streamsSelector()
}
