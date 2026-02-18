import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp } from 'antd';
import App from './App';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AntdApp>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter basename="/xxl-job-admin">
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </AntdApp>
  </React.StrictMode>,
);
