import React, { useEffect } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntdApp, theme as antTheme } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { useTranslation } from 'react-i18next';
import './locales';
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

const ANTD_LOCALE_MAP: Record<string, typeof enUS> = {
  en: enUS,
  zh: zhCN,
};

function ThemedApp() {
  const resolved = useThemeStore((s) => s.resolved);
  useSystemThemeListener();
  const { i18n } = useTranslation();

  const themeConfig = resolved === 'dark' ? darkTheme : lightTheme;
  const algorithm =
    resolved === 'dark' ? fixedDarkAlgorithm : antTheme.defaultAlgorithm;

  const antdLocale = ANTD_LOCALE_MAP[i18n.language] ?? enUS;

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolved);
    document.documentElement.style.colorScheme = resolved;
  }, [resolved]);

  useEffect(() => {
    document.documentElement.lang = i18n.language;
  }, [i18n.language]);

  return (
    <ConfigProvider theme={{ ...themeConfig, algorithm }} locale={antdLocale}>
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
