import { Menu } from 'antd'
import React, {useState} from 'react'
import { Link } from 'react-router-dom'
import {imageDisplayMenuKey, settingsMenuKey, useAppContext} from "../AppContext";

export const MenuBar = (): JSX.Element => {
  const {mainMenuSelectedKeys, setMainMenuSelectedKeys} = useAppContext()

  return (
    <Menu
      mode='horizontal'
      selectedKeys={mainMenuSelectedKeys}
      onSelect={info => setMainMenuSelectedKeys(info.selectedKeys)}
    >
      <Menu.Item key={settingsMenuKey}>
        <Link to='/'>Settings</Link>
      </Menu.Item>
      <Menu.Item key={imageDisplayMenuKey}>
        <Link to={`/${imageDisplayMenuKey}`}>Images</Link>
      </Menu.Item>
    </Menu>
  )
}
