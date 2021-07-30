import React, {createContext, useContext} from "react"
import type {ServerInfo, StreamInfo} from "./models/Models";
import type {WebSocketLike} from "react-use-websocket/dist/lib/types";

// Application context: Holds values and functions that are shared by different components in the app
export type AppContextState = {
  serverInfo: ServerInfo | undefined,
  setServerInfo: (_: ServerInfo | undefined) => void,
  availableStreams: Array<StreamInfo>,
  setAvailableStreams: (_: Array<StreamInfo>) => void,
  selectedStream: StreamInfo | undefined,
  setSelectedStream: (_: StreamInfo | undefined) => void,
  mainMenuSelectedKeys: Array<string>,
  setMainMenuSelectedKeys: (_: Array<string>) => void,
  currentWebSocket: WebSocketLike | null,
  setCurrentWebSocket: (_: WebSocketLike | null) => void
}

const appContextDefaultValue: AppContextState = {
  serverInfo: {host: 'localhost', port: 7777},
  setServerInfo: (_: ServerInfo | undefined) => {},
  availableStreams: [],
  setAvailableStreams: (_: Array<StreamInfo>) => {},
  selectedStream: undefined,
  setSelectedStream: (_: StreamInfo | undefined) => {},
  mainMenuSelectedKeys: ['settings'],
  setMainMenuSelectedKeys: (_: Array<string>) => {},
  currentWebSocket: null,
  setCurrentWebSocket: (_: WebSocketLike | null) => {}
}

export const appContext = createContext<AppContextState>(appContextDefaultValue)
export const useAppContext = () => useContext(appContext)

// --- Constants ---
export const settingsMenuKey = 'settings'
export const imageDisplayMenuKey = 'imageDisplay'
export const adminRoute  = "/vbds/admin/streams"
export const accessRoute = "/vbds/access/streams"
