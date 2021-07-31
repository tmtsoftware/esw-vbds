import {Button, Form, Input, Layout, Typography} from 'antd'
import {Content} from 'antd/lib/layout/layout'
import React from 'react'
import styles from './SettingsForm.module.css'
import {imageDisplayMenuKey, useAppContext} from "../../AppContext";
import type {ServerInfo} from "../../models/Models";
import { useHistory } from 'react-router-dom';

export const SettingsForm = (): JSX.Element => {
  const {serverInfo, setServerInfo, setMainMenuSelectedKeys} = useAppContext()
  const history = useHistory();

  const layout = {
    labelCol: {span: 12},
    wrapperCol: {span: 12}
  }

  function onFinish(serverInfo: ServerInfo) {
    setServerInfo(serverInfo)
    setMainMenuSelectedKeys([imageDisplayMenuKey])
    history.push(`/${imageDisplayMenuKey}`);
  }

  return (
    <Layout>
      <Content className={styles.content}>
        <Form
          {...layout}
          initialValues={{host: serverInfo?.host, port: serverInfo?.port}}
          onFinish={onFinish}
          className={styles.formBody}>
          <Form.Item className={styles.formHeader}>
            <Typography.Title level={4}>{`VBDS Server Settings:`}</Typography.Title>
          </Form.Item>
          <Form.Item
            label='VBDS Server Host'
            name='host'
            rules={[
              {required: true, message: 'Please enter the VBDS server host name or IP address'}
            ]}>
            <Input role='Host' className={styles.formInput} value={serverInfo?.host}/>
          </Form.Item>

          <Form.Item
            label='VBDS Server Port'
            name='port'
            rules={[
              {required: true, message: 'Please enter VBDS server port number'}
            ]}>
            <Input role='Port' className={styles.formInput} value={serverInfo?.port}/>
          </Form.Item>

          <Form.Item wrapperCol={{offset: 12, span: 12}}>
            <Button type='primary' htmlType='submit' role='Submit'>
              Submit
            </Button>
          </Form.Item>
        </Form>
      </Content>
    </Layout>
  )
}
