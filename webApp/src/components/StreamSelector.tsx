import React, {useEffect, useState} from 'react'
import {Select, Typography} from "antd";
import {useAppContext} from "../AppContext";
import {get} from '../utils/Http'
import type {StreamInfo} from "../models/Models";

const {Option} = Select;

export const StreamSelector = (): JSX.Element => {

  const {serverInfo, availableStreams, setAvailableStreams, selectedStream, setSelectedStream} = useAppContext()

  const {Text} = Typography;

  function changeStream(selectedStream: string | undefined) {
    console.log(`XXX Selected stream is ${selectedStream}`)
  }

  useEffect(() => {
    console.log(`XXX StreamSelector: serverInfo changed: ${serverInfo?.host}:${serverInfo?.port}`)
    const uri = serverInfo ? `http://${serverInfo.host}:${serverInfo.port}/vbds/admin/streams` : undefined
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
          style={{marginLeft: 10}}
          placeholder="Select an image stream"
          value={selectedStream?.name}
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
