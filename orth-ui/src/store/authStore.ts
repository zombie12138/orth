import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { JwtUserInfo, LoginResponse } from '../types/auth';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userInfo: JwtUserInfo | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (res: LoginResponse) => void;
  updateTokens: (res: LoginResponse) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userInfo: null,
      isAuthenticated: false,
      isAdmin: false,
      login: (res) =>
        set({
          accessToken: res.accessToken,
          refreshToken: res.refreshToken,
          userInfo: res.userInfo,
          isAuthenticated: true,
          isAdmin: res.userInfo.role === 1,
        }),
      updateTokens: (res) =>
        set({
          accessToken: res.accessToken,
          refreshToken: res.refreshToken,
          userInfo: res.userInfo,
          isAuthenticated: true,
          isAdmin: res.userInfo.role === 1,
        }),
      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userInfo: null,
          isAuthenticated: false,
          isAdmin: false,
        }),
    }),
    { name: 'auth-storage' },
  ),
);
