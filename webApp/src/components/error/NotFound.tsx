import { Result } from 'antd'
import React from 'react'

export const NotFound = (): JSX.Element => {
  return (
    <Result
      status='404'
      title='404'
      subTitle='Sorry, the page you visited does not exist.'
    />
  )
}
