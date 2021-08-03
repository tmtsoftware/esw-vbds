import { Menu } from 'antd'
import React, {useState} from 'react'
import { Link } from 'react-router-dom'
import {imageDisplayMenuKey, useAppContext} from "../AppContext";

export const MenuBar = (): JSX.Element => {
  const {mainMenuSelectedKeys, setMainMenuSelectedKeys} = useAppContext()

  return (
    <Menu
      mode='horizontal'
      selectedKeys={mainMenuSelectedKeys}
      onSelect={info => setMainMenuSelectedKeys(info.selectedKeys)}
    >
      <Menu.Item key={imageDisplayMenuKey}>
        <Link to={`/`}>Images</Link>
      </Menu.Item>
    </Menu>
  )
}
