import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Layout, Dropdown, Space, Segmented, Button, theme } from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  KeyOutlined,
  DesktopOutlined,
  SunOutlined,
  MoonOutlined,
  MenuOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { useThemeStore } from '../../store/themeStore';
import { logout as apiLogout } from '../../api/auth';
import PasswordChangeModal from '../PasswordChangeModal';

const { Header } = Layout;

interface Props {
  isMobile: boolean;
  onMenuClick: () => void;
}

export default function AppHeader({ isMobile, onMenuClick }: Props) {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const { t, i18n } = useTranslation();
  const userInfo = useAuthStore((s) => s.userInfo);
  const logout = useAuthStore((s) => s.logout);
  const [pwdOpen, setPwdOpen] = useState(false);

  const themeMode = useThemeStore((s) => s.mode);
  const setThemeMode = useThemeStore((s) => s.setMode);

  const currentLang = i18n.language.startsWith('zh') ? 'zh' : 'en';

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
      label: t('changePassword'),
      onClick: () => setPwdOpen(true),
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: t('logout'),
      onClick: handleLogout,
    },
  ];

  return (
    <Header
      style={{
        background: token.colorBgContainer,
        padding: isMobile ? '0 12px' : '0 24px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 16,
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      {isMobile ? (
        <Button
          type="text"
          icon={<MenuOutlined />}
          onClick={onMenuClick}
          style={{ fontSize: 18 }}
        />
      ) : (
        <div />
      )}
      <Space>
        <Segmented
          size="small"
          value={currentLang}
          onChange={(v) => i18n.changeLanguage(v as string)}
          options={[
            { value: 'en', label: 'EN' },
            { value: 'zh', label: '中文' },
          ]}
        />
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
            {!isMobile && <span>{userInfo?.username ?? 'User'}</span>}
          </Space>
        </Dropdown>
      </Space>
      <PasswordChangeModal open={pwdOpen} onClose={() => setPwdOpen(false)} />
    </Header>
  );
}
