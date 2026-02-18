import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import { Spin } from 'antd';
import AuthGuard from './components/AuthGuard';
import AdminGuard from './components/AdminGuard';
import AppLayout from './components/Layout/AppLayout';

const LoginPage = lazy(() => import('./pages/Login'));
const DashboardPage = lazy(() => import('./pages/Dashboard'));
const JobsPage = lazy(() => import('./pages/Jobs'));
const LogsPage = lazy(() => import('./pages/Logs'));
const ExecutorGroupsPage = lazy(() => import('./pages/ExecutorGroups'));
const UsersPage = lazy(() => import('./pages/Users'));

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
            <Route path="/jobs" element={<JobsPage />} />
            <Route path="/logs" element={<LogsPage />} />
            <Route element={<AdminGuard />}>
              <Route path="/executor-groups" element={<ExecutorGroupsPage />} />
              <Route path="/users" element={<UsersPage />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  );
}
