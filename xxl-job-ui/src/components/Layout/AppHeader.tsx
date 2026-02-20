import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Layout, Dropdown, Space, Segmented, theme } from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  KeyOutlined,
  DesktopOutlined,
  SunOutlined,
  MoonOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../store/authStore';
import { useThemeStore } from '../../store/themeStore';
import { logout as apiLogout } from '../../api/auth';
import PasswordChangeModal from '../PasswordChangeModal';

const { Header } = Layout;

export default function AppHeader() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const userInfo = useAuthStore((s) => s.userInfo);
  const logout = useAuthStore((s) => s.logout);
  const [pwdOpen, setPwdOpen] = useState(false);

  const themeMode = useThemeStore((s) => s.mode);
  const setThemeMode = useThemeStore((s) => s.setMode);

  const handleLogout = async () => {
    try {
      await apiLogout();
    } catch {
      // ignore logout API errors
    }
    logout();
    navigate('/login', { replace: true });
  };

  const items = [
    {
      key: 'password',
      icon: <KeyOutlined />,
      label: 'Change Password',
      onClick: () => setPwdOpen(true),
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Logout',
      onClick: handleLogout,
    },
  ];

  return (
    <Header
      style={{
        background: token.colorBgContainer,
        padding: '0 24px',
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
        gap: 16,
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
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
      <Dropdown menu={{ items }} trigger={['click']}>
        <Space style={{ cursor: 'pointer' }}>
          <UserOutlined />
          <span>{userInfo?.username ?? 'User'}</span>
        </Space>
      </Dropdown>
      <PasswordChangeModal open={pwdOpen} onClose={() => setPwdOpen(false)} />
    </Header>
  );
}
