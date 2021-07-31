import {AuthContextProvider, LocationService} from '@tmtsoftware/esw-ts'
import React, {useState} from 'react'
import {BrowserRouter as Router} from 'react-router-dom'
import 'antd/dist/antd.css'
import {MenuBar} from './components/Menu'
import {AppConfig} from './config/AppConfig'
import {LocationServiceProvider} from './contexts/LocationServiceContext'
import {useQuery} from './hooks/useQuery'
import {Routes} from './routes/Routes'
import {appContext, AppContextState} from './AppContext'
import type {ServerInfo, StreamInfo} from "./models/Models";
import type {WebSocketLike} from "react-use-websocket/dist/lib/types";

const basename =
  import.meta.env.NODE_ENV === 'production' ? AppConfig.applicationName : ''

export const App = (): JSX.Element => {
  const {data: locationService, loading, error} = useQuery(LocationService)
  const [serverInfo, setServerInfo] = useState<ServerInfo | undefined>({host: 'localhost', port: 7777})
  const [availableStreams, setAvailableStreams] = useState<Array<StreamInfo>>([])
  const [selectedStream, setSelectedStream] = useState<StreamInfo | undefined>(undefined)
  const [mainMenuSelectedKeys, setMainMenuSelectedKeys] = useState<Array<string>>(['settings'])
  const [currentWebSocket, setCurrentWebSocket] = useState<WebSocketLike | null>(null)

  const appContextValues: AppContextState = {
    serverInfo,
    setServerInfo,
    availableStreams,
    setAvailableStreams,
    selectedStream,
    setSelectedStream,
    mainMenuSelectedKeys,
    setMainMenuSelectedKeys,
    currentWebSocket,
    setCurrentWebSocket
  }

  if (loading) return (
    <appContext.Provider value={appContextValues}>
      <div>Loading...</div>
    </appContext.Provider>
  )
  if (error || !locationService)
    return <div>Location Service not Available, reason {error?.message}</div>

  return (
    <div>
      <LocationServiceProvider locationService={locationService}>
        <AuthContextProvider>
          <appContext.Provider value={appContextValues}>
            <Router basename={basename}>
              <MenuBar/>
              <Routes/>
            </Router>
          </appContext.Provider>
        </AuthContextProvider>
      </LocationServiceProvider>
    </div>
  )
}