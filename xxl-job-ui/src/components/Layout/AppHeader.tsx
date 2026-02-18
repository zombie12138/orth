import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Layout, Dropdown, Space } from 'antd';
import { UserOutlined, LogoutOutlined, KeyOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../store/authStore';
import { logout as apiLogout } from '../../api/auth';
import PasswordChangeModal from '../PasswordChangeModal';

const { Header } = Layout;

export default function AppHeader() {
  const navigate = useNavigate();
  const userInfo = useAuthStore((s) => s.userInfo);
  const logout = useAuthStore((s) => s.logout);
  const [pwdOpen, setPwdOpen] = useState(false);

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
        background: '#fff',
        padding: '0 24px',
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
        borderBottom: '1px solid #f0f0f0',
      }}
    >
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
