import { Navigate, Outlet } from 'react-router';
import { useAuthStore } from '../store/authStore';

export default function AdminGuard() {
  const isAdmin = useAuthStore((s) => s.isAdmin);
  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}
