import { useState } from 'react';
import { useNavigate } from 'react-router';
import { App, Card, Form, Input, Button, Segmented, theme } from 'antd';
import {
  UserOutlined,
  LockOutlined,
  DesktopOutlined,
  SunOutlined,
  MoonOutlined,
} from '@ant-design/icons';
import { login as apiLogin } from '../../api/auth';
import { useAuthStore } from '../../store/authStore';
import { useConfigStore } from '../../store/configStore';
import { useThemeStore } from '../../store/themeStore';

export default function LoginPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const storeLogin = useAuthStore((s) => s.login);
  const loadConfig = useConfigStore((s) => s.loadConfig);
  const { token } = theme.useToken();
  const themeMode = useThemeStore((s) => s.mode);
  const setThemeMode = useThemeStore((s) => s.setMode);
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await apiLogin(values);
      storeLogin(res);
      await loadConfig();
      navigate('/dashboard', { replace: true });
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: token.colorBgLayout,
        position: 'relative',
      }}
    >
      <div style={{ position: 'absolute', top: 16, right: 24 }}>
        <Segmented
          size="small"
          value={themeMode}
          onChange={(v) => setThemeMode(v as 'system' | 'light' | 'dark')}
          options={[
            { value: 'system', icon: <DesktopOutlined /> },
            { value: 'light', icon: <SunOutlined /> },
            { value: 'dark', icon: <MoonOutlined /> },
          ]}
        />
      </div>
      <Card
        title="Orth Job Admin"
        style={{ width: 400 }}
        styles={{ header: { textAlign: 'center', fontSize: 20, fontWeight: 700 } }}
      >
        <Form onFinish={onFinish} size="large">
          <Form.Item
            name="username"
            rules={[{ required: true, message: 'Please enter username' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="Username" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: 'Please enter password' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="Password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              Login
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
