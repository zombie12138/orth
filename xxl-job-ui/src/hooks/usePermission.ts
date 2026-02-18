import { useMemo } from 'react';
import { useAuthStore } from '../store/authStore';

export function usePermission() {
  const userInfo = useAuthStore((s) => s.userInfo);
  const isAdmin = useAuthStore((s) => s.isAdmin);

  const permittedGroups = useMemo(() => {
    if (!userInfo?.permission) return new Set<number>();
    return new Set(
      userInfo.permission
        .split(',')
        .filter(Boolean)
        .map(Number),
    );
  }, [userInfo?.permission]);

  const canAccessGroup = (groupId: number) => {
    return isAdmin || permittedGroups.has(groupId);
  };

  return { isAdmin, canAccessGroup, permittedGroups };
}
