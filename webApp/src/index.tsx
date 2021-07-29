import { setAppName } from '@tmtsoftware/esw-ts'
import React from 'react'
import { render } from 'react-dom'
import { App } from './App'
import { AppConfig } from './config/AppConfig'
import './index.css'

setAppName(AppConfig.applicationName)

render(<App />, document.getElementById('root'))
