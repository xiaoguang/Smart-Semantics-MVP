import { useState } from 'react';
import {
  Alert, Button, Drawer, Form, Input, Tooltip, Typography, message,
} from 'antd';
import { CopyOutlined, EyeInvisibleOutlined, EyeOutlined, LockOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { demoUsers } from './runtime.ts';
import { presentLoginFailure } from './login-copy.ts';
import './login-page.css';

export default function LoginPage({ onLogin }: { onLogin(username: string, password: string): void }) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [visiblePasswords, setVisiblePasswords] = useState<Set<string>>(new Set());
  const [form] = Form.useForm();
  const submit = (values: { username: string; password: string }) => {
    try { onLogin(values.username, values.password); }
    catch (error) { message.error(presentLoginFailure(error)); }
  };
  const fill = (username: string, password: string) => {
    form.setFieldsValue({ username, password }); setDrawerOpen(false); message.success('账号已填入');
  };
  const copy = async (text: string, label: string) => {
    try {
      if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text);
      else {
        const input = document.createElement('textarea');
        input.value = text;
        input.setAttribute('readonly', '');
        input.style.position = 'fixed';
        input.style.opacity = '0';
        document.body.appendChild(input);
        input.select();
        if (!document.execCommand('copy')) throw new Error('浏览器拒绝复制');
        input.remove();
      }
      message.success(`${label}已复制`);
    } catch {
      message.error(`无法自动复制${label}，请显示后手动选择复制`);
    }
  };

  return <main className="demo-login-page">
    <section className="demo-login-card">
      <div className="demo-login-brand"><TeamOutlined /><span>灵光语义建模工作台</span></div>
      <Typography.Title level={2}>登录协作体验</Typography.Title>
      <Typography.Paragraph type="secondary">以不同角色体验个人草稿、审核与发布。每个浏览器标签页可以登录不同用户。</Typography.Paragraph>
      <Alert type="warning" showIcon message="浏览器内体验账号" description="账号和密码仅用于本地协作演示，不具备生产环境安全性。" />
      <Form form={form} layout="vertical" onFinish={submit} requiredMark={false}>
        <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input size="large" prefix={<UserOutlined />} autoComplete="username" placeholder="用户名区分大小写" />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password size="large" prefix={<LockOutlined />} autoComplete="current-password" />
        </Form.Item>
        <Button block type="primary" size="large" htmlType="submit">登录</Button>
      </Form>
      <Button type="link" onClick={() => setDrawerOpen(true)}>查看体验账号</Button>
    </section>
    <Drawer title="体验账号" width="min(560px, 100vw)" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
      <Alert type="info" showIcon message="统一体验账号" description="登录后只会看到当前账号有权限访问的模型项目。" />
      <section className="demo-account-list">
        {demoUsers.map((user) => {
          const shown = visiblePasswords.has(user.userId);
          return <div className="demo-account-row" key={user.userId}>
            <div className="demo-account-identity"><strong>{user.displayName}</strong><span>{user.username}</span></div>
            <code>{shown ? user.demoPassword : '••••••••••••'}</code>
            <div className="demo-account-actions">
              <Tooltip title={shown ? '隐藏密码' : '查看密码'}><Button aria-label={shown ? `隐藏 ${user.username} 的密码` : `查看 ${user.username} 的密码`} type="text" icon={shown ? <EyeInvisibleOutlined /> : <EyeOutlined />} onClick={() => setVisiblePasswords((current) => {
                const next = new Set(current); shown ? next.delete(user.userId) : next.add(user.userId); return next;
              })} /></Tooltip>
              <Tooltip title="复制用户名"><Button aria-label={`复制 ${user.username} 用户名`} type="text" icon={<CopyOutlined />} onClick={() => void copy(user.username, '用户名')} /></Tooltip>
              <Tooltip title="复制密码"><Button aria-label={`复制 ${user.username} 密码`} type="text" icon={<LockOutlined />} onClick={() => void copy(user.demoPassword, '密码')} /></Tooltip>
              <Button size="small" onClick={() => fill(user.username, user.demoPassword)}>填入</Button>
            </div>
          </div>;
        })}
      </section>
    </Drawer>
  </main>;
}
