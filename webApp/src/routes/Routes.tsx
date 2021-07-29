import React from 'react'
import { Route, Switch } from 'react-router-dom'
import { NotFound } from '../components/error/NotFound'
import { SettingsTab } from '../components/pages/SettingsTab'
import { ImageSubscriber } from '../components/pages/ImageSubscriber'
import {imageDisplayMenuKey} from "../AppContext";

export const Routes = (): JSX.Element => {
  return (
    <Switch>
      <Route exact path='/' component={SettingsTab} />
      <Route exact path={`/${imageDisplayMenuKey}`} component={ImageSubscriber} />
      <Route path='*' component={NotFound} />
    </Switch>
  )
}

