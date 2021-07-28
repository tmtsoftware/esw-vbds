import React, {createContext, useContext} from "react"
import type {ServerInfo, StreamInfo} from "./models/Models";

// Application context: Holds values and functions that are shared by different components in the app
export type AppContextState = {
  serverInfo: ServerInfo | undefined,
  setServerInfo: (_: ServerInfo | undefined) => void,
  availableStreams: Array<StreamInfo>,
  setAvailableStreams: (_: Array<StreamInfo>) => void,
  selectedStream: StreamInfo | undefined,
  setSelectedStream: (_: StreamInfo | undefined) => void
}

const appContextDefaultValue: AppContextState = {
  serverInfo: {host: 'localhost', port: 7777},
  setServerInfo: (_: ServerInfo | undefined) => {},
  availableStreams: [],
  setAvailableStreams: (_: Array<StreamInfo>) => {},
  selectedStream: undefined,
  setSelectedStream: (_: StreamInfo | undefined) => {}
}

export const appContext = createContext<AppContextState>(appContextDefaultValue)
export const useAppContext = () => useContext(appContext)
