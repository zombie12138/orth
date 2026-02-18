import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import { Spin } from 'antd';
import AuthGuard from './components/AuthGuard';
import AppLayout from './components/Layout/AppLayout';

const LoginPage = lazy(() => import('./pages/Login'));
const DashboardPage = lazy(() => import('./pages/Dashboard'));

function Loading() {
  return (
    <div style={{ textAlign: 'center', padding: 120 }}>
      <Spin size="large" />
    </div>
  );
}

export default function App() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<AuthGuard />}>
          <Route element={<AppLayout />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  );
}
