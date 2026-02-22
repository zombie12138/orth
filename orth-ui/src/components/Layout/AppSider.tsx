import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { Layout, Menu, Drawer, theme } from 'antd';
import {
  DashboardOutlined,
  ScheduleOutlined,
  FileTextOutlined,
  ClusterOutlined,
  UserOutlined,
  BookOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useConfigStore } from '../../store/configStore';
import { mapMenuUrl } from '../../utils/constants';
import type { MenuItem } from '../../types/menu';

const { Sider } = Layout;

const ICON_MAP: Record<string, React.ReactNode> = {
  '/dashboard': <DashboardOutlined />,
  '/jobinfo': <ScheduleOutlined />,
  '/joblog': <FileTextOutlined />,
  '/jobgroup': <ClusterOutlined />,
  '/user': <UserOutlined />,
  '/help': <BookOutlined />,
};

interface Props {
  isMobile: boolean;
  drawerOpen: boolean;
  onClose: () => void;
}

function buildMenuItems(items: MenuItem[], t: TFunction): any[] {
  return items
    .filter((m) => m.type <= 1 && m.status === 0)
    .sort((a, b) => a.order - b.order)
    .map((m) => {
      const frontendUrl = mapMenuUrl(m.url);
      const menuKey = `menu.${m.url}`;
      const translated = t(menuKey, { defaultValue: '' });
      const item: any = {
        key: frontendUrl,
        icon: ICON_MAP[m.url],
        label: translated || m.name,
      };
      if (m.children?.length) {
        const children = buildMenuItems(m.children, t);
        if (children.length > 0) {
          item.children = children;
        }
      }
      return item;
    });
}

export default function AppSider({ isMobile, drawerOpen, onClose }: Props) {
  const navigate = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();
  const { t } = useTranslation();
  const menus = useConfigStore((s) => s.menus);
  const [collapsed, setCollapsed] = useState(false);

  const menuItems = buildMenuItems(menus, t);

  // Determine selected key from current path
  const selectedKey =
    '/' + (location.pathname.split('/').filter(Boolean)[0] ?? 'dashboard');

  const handleMenuClick = ({ key }: { key: string }) => {
    if (key === '/help') {
      window.open('https://github.com/zombie12138/orth', '_blank');
    } else {
      navigate(key);
    }
    if (isMobile) {
      onClose();
    }
  };

  const logo = (
    <div
      style={{
        height: 48,
        margin: 16,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: token.colorText,
        fontWeight: 700,
        fontSize: isMobile || !collapsed ? 20 : 16,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
      }}
    >
      {!isMobile && collapsed ? t('brandLetter') : t('brandShort')}
    </div>
  );

  const menu = (
    <Menu
      mode="inline"
      selectedKeys={[selectedKey]}
      items={menuItems}
      onClick={handleMenuClick}
    />
  );

  if (isMobile) {
    return (
      <Drawer
        placement="left"
        open={drawerOpen}
        onClose={onClose}
        width={250}
        styles={{ body: { padding: 0 } }}
      >
        {logo}
        {menu}
      </Drawer>
    );
  }

  return (
    <Sider
      collapsible
      collapsed={collapsed}
      onCollapse={setCollapsed}
      style={{ minHeight: '100vh' }}
    >
      {logo}
      {menu}
    </Sider>
  );
}
