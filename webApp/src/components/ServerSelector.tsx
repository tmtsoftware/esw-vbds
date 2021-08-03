import React, {useEffect, useState} from 'react'
import {Select, Typography} from "antd";
import {useAppContext} from "../AppContext";
import type {ServerInfo} from "../models/Models";
import {useLocationService} from "../contexts/LocationServiceContext";

const {Option} = Select;

export const ServerSelector = (): JSX.Element => {

  const {serverInfo, setServerInfo} = useAppContext()
  const [servers, setServers] = useState<Array<ServerInfo>>([])

  const locationService = useLocationService()

  function makeServerInfo(name: string, uri: string): ServerInfo {
    const url = new URL(uri)
    const port: number = +url.port
    return {
      name: name,
      host: url.hostname,
      port: port
    }
  }

  useEffect(() => {
    locationService.listByConnectionType('http')
      .then(ar =>
        setServers(ar.filter(loc =>
          loc.connection.prefix.subsystem == "ESW")
          .map(loc =>
            makeServerInfo(loc.connection.prefix.componentName, loc.uri))))
  }, [])

  const {Text} = Typography;

  function serverSelected(name: string | undefined) {
    const s = servers.find(s => s.name == name)
    setServerInfo(s)
  }

  function serverSelector(): JSX.Element {
    const defaultOption =
      <Option key={'None'} value={'None'}>
        {'None'}
      </Option>

    const options = Array.of(defaultOption).concat(servers.map(server => {
      return (
        <Option key={server.name} value={server.name}>
          {`${server.name} [${server.host}:${server.port}]`}
        </Option>
      )
    }))

    return (
      <div style={{margin: 10, padding: 10}}>
        <Text>VBDS Server:</Text>
        <Select
          style={{marginLeft: 10, minWidth: '60ch'}}
          placeholder="Select a VBDS Server"
          value={serverInfo ? serverInfo.name : 'None'}
          onChange={serverSelected}
          disabled={false}
        >
          {options}
        </Select>
      </div>
    )
  }

  return serverSelector()
}
