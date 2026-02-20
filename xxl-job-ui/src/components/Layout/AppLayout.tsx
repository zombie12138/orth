import { useEffect } from 'react';
import { Outlet } from 'react-router';
import { Layout, Spin, theme } from 'antd';
import { useConfigStore } from '../../store/configStore';
import AppHeader from './AppHeader';
import AppSider from './AppSider';

const { Content } = Layout;

export default function AppLayout() {
  const loaded = useConfigStore((s) => s.loaded);
  const loadConfig = useConfigStore((s) => s.loadConfig);
  const { token } = theme.useToken();

  useEffect(() => {
    if (!loaded) {
      loadConfig();
    }
  }, [loaded, loadConfig]);

  if (!loaded) {
    return (
      <div
        style={{
          height: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: token.colorBgLayout,
        }}
      >
        <Spin size="large" />
      </div>
    );
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <AppSider />
      <Layout>
        <AppHeader />
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
