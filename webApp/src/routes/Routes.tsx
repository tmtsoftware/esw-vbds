import React from 'react'
import { Route, Switch } from 'react-router-dom'
import { NotFound } from '../components/error/NotFound'
import { ImageSubscriber } from '../components/pages/ImageSubscriber'

export const Routes = (): JSX.Element => {
  return (
    <Switch>
      <Route exact path={`/`} component={ImageSubscriber} />
      <Route path='*' component={NotFound} />
    </Switch>
  )
}

