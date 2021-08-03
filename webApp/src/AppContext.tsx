import React, {createContext, useContext} from "react"
import type {ServerInfo, StreamInfo} from "./models/Models";

// --- Constants ---
export const imageDisplayMenuKey = 'imageDisplay'
export const adminRoute  = "/vbds/admin/streams"
export const accessRoute = "/vbds/access/streams"

// Application context: Holds values and functions that are shared by different components in the app
export type AppContextState = {
  serverInfo: ServerInfo | undefined,
  setServerInfo: (_: ServerInfo | undefined) => void,
  availableStreams: Array<StreamInfo>,
  setAvailableStreams: (_: Array<StreamInfo>) => void,
  selectedStream: StreamInfo | undefined,
  setSelectedStream: (_: StreamInfo | undefined) => void,
  mainMenuSelectedKeys: Array<string>,
  setMainMenuSelectedKeys: (_: Array<string>) => void
}

const appContextDefaultValue: AppContextState = {
  serverInfo: undefined,
  setServerInfo: (_: ServerInfo | undefined) => {},
  availableStreams: [],
  setAvailableStreams: (_: Array<StreamInfo>) => {},
  selectedStream: undefined,
  setSelectedStream: (_: StreamInfo | undefined) => {},
  mainMenuSelectedKeys: [imageDisplayMenuKey],
  setMainMenuSelectedKeys: (_: Array<string>) => {}
}

export const appContext = createContext<AppContextState>(appContextDefaultValue)
export const useAppContext = () => useContext(appContext)

