import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Card, Form, Input, Button, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { login as apiLogin } from '../../api/auth';
import { useAuthStore } from '../../store/authStore';
import { useConfigStore } from '../../store/configStore';

export default function LoginPage() {
  const navigate = useNavigate();
  const storeLogin = useAuthStore((s) => s.login);
  const loadConfig = useConfigStore((s) => s.loadConfig);
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
        background: '#f0f2f5',
      }}
    >
      <Card
        title="Orth Job Admin"
        style={{ width: 400 }}
        headStyle={{ textAlign: 'center', fontSize: 20, fontWeight: 700 }}
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
