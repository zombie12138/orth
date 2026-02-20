import React, { useEffect } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntdApp, theme as antTheme } from 'antd';
import App from './App';
import { useThemeStore } from './store/themeStore';
import { useSystemThemeListener } from './hooks/useSystemThemeListener';
import { lightTheme, darkTheme, fixedDarkAlgorithm } from './theme/themeConfig';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

function ThemedApp() {
  const resolved = useThemeStore((s) => s.resolved);
  useSystemThemeListener();

  const themeConfig = resolved === 'dark' ? darkTheme : lightTheme;
  const algorithm =
    resolved === 'dark' ? fixedDarkAlgorithm : antTheme.defaultAlgorithm;

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolved);
    document.documentElement.style.colorScheme = resolved;
  }, [resolved]);

  return (
    <ConfigProvider theme={{ ...themeConfig, algorithm }}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter basename="/xxl-job-admin">
            <App />
          </BrowserRouter>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemedApp />
  </React.StrictMode>,
);
