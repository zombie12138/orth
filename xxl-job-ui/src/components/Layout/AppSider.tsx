import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { Layout, Menu } from 'antd';
import {
  DashboardOutlined,
  ScheduleOutlined,
  FileTextOutlined,
  ClusterOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useConfigStore } from '../../store/configStore';
import { mapMenuUrl } from '../../utils/constants';
import type { MenuItem } from '../../types/menu';

const { Sider } = Layout;

const ICON_MAP: Record<string, React.ReactNode> = {
  '/': <DashboardOutlined />,
  '/jobinfo': <ScheduleOutlined />,
  '/joblog': <FileTextOutlined />,
  '/jobgroup': <ClusterOutlined />,
  '/user': <UserOutlined />,
};

function buildMenuItems(items: MenuItem[]): any[] {
  return items
    .filter((m) => m.type <= 1 && m.status === 0)
    .sort((a, b) => a.order - b.order)
    .map((m) => {
      const frontendUrl = mapMenuUrl(m.url);
      const item: any = {
        key: frontendUrl,
        icon: ICON_MAP[m.url],
        label: m.name,
      };
      if (m.children?.length) {
        const children = buildMenuItems(m.children);
        if (children.length > 0) {
          item.children = children;
        }
      }
      return item;
    });
}

export default function AppSider() {
  const navigate = useNavigate();
  const location = useLocation();
  const menus = useConfigStore((s) => s.menus);
  const [collapsed, setCollapsed] = useState(false);

  const menuItems = buildMenuItems(menus);

  // Determine selected key from current path
  const selectedKey =
    '/' + (location.pathname.split('/').filter(Boolean)[0] ?? 'dashboard');

  return (
    <Sider
      collapsible
      collapsed={collapsed}
      onCollapse={setCollapsed}
      theme="dark"
      style={{ minHeight: '100vh' }}
    >
      <div
        style={{
          height: 48,
          margin: 16,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontWeight: 700,
          fontSize: collapsed ? 16 : 20,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
        }}
      >
        {collapsed ? 'O' : 'Orth Job'}
      </div>
      <Menu
        theme="dark"
        mode="inline"
        selectedKeys={[selectedKey]}
        items={menuItems}
        onClick={({ key }) => navigate(key)}
      />
    </Sider>
  );
}
