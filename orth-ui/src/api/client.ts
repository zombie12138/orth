import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { NotificationInstance } from 'antd/es/notification/interface';
import type { ApiResponse, PageModel } from '../types/api';
import type { LoginResponse, RefreshRequest } from '../types/auth';

// Strip Java exception class names from error messages.
// "java.lang.IllegalArgumentException: bad input" → "bad input"
// "java.lang.NullPointerException" (no message) → "NullPointerException"
function cleanErrorMessage(msg: string): string {
    const match = msg.match(/^[\w.]+\.(\w+Exception)(?::\s*(.+))?$/);
    if (!match) return msg;
    return match[2]?.trim() || match[1]!;
}

// Notification instance injected from React tree via App.useApp().
// Must be set before showError() is called (see main.tsx).
let notificationApi: NotificationInstance | null = null;

export function setNotificationApi(api: NotificationInstance): void {
    notificationApi = api;
}

// Display a persistent error notification that the user must manually dismiss.
export function showError(error: unknown): void {
    let msg: string;
    if (error instanceof AxiosError) {
        msg = error.response?.data?.msg ?? error.message;
    } else if (error instanceof Error) {
        msg = error.message;
    } else {
        msg = String(error);
    }
    if (notificationApi) {
        notificationApi.error({
            message: 'Error',
            description: msg,
            duration: 0,
        });
    } else {
        console.error('[showError] notification API not initialized:', msg);
    }
}

const client = axios.create({
  baseURL: '/orth-admin',
  timeout: 30000,
});

const AUTH_SKIP_URLS = ['/api/v1/auth/login', '/api/v1/auth/refresh'];

// Request interceptor: attach JWT token
client.interceptors.request.use((config) => {
  if (!AUTH_SKIP_URLS.some((url) => config.url?.endsWith(url))) {
    const raw = localStorage.getItem('auth-storage');
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as { state?: { accessToken?: string } };
        const token = parsed.state?.accessToken;
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
      } catch {
        // ignore parse errors
      }
    }
  }
  return config;
});

// Response interceptor: auto-refresh on 401
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach((p) => {
    if (token) {
      p.resolve(token);
    } else {
      p.reject(error);
    }
  });
  failedQueue = [];
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    if (
      error.response?.status !== 401 ||
      originalRequest._retry ||
      AUTH_SKIP_URLS.some((url) => originalRequest.url?.endsWith(url))
    ) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return client(originalRequest);
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const raw = localStorage.getItem('auth-storage');
      if (!raw) throw new Error('No auth storage');
      const parsed = JSON.parse(raw) as {
        state?: { refreshToken?: string };
      };
      const refreshToken = parsed.state?.refreshToken;
      if (!refreshToken) throw new Error('No refresh token');

      const { data } = await axios.post<ApiResponse<LoginResponse>>(
        '/orth-admin/api/v1/auth/refresh',
        { refreshToken } as RefreshRequest,
      );

      if (!data.success) throw new Error(data.msg ?? 'Refresh failed');

      const newAccess = data.data.accessToken;
      const newRefresh = data.data.refreshToken;

      // Update stored tokens
      const store = JSON.parse(raw) as { state: Record<string, unknown> };
      store.state.accessToken = newAccess;
      store.state.refreshToken = newRefresh;
      store.state.userInfo = data.data.userInfo;
      localStorage.setItem('auth-storage', JSON.stringify(store));

      processQueue(null, newAccess);

      originalRequest.headers.Authorization = `Bearer ${newAccess}`;
      return client(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      localStorage.removeItem('auth-storage');
      window.location.href = '/orth-admin/login';
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

// Helper to unwrap ApiResponse
export async function unwrap<T>(
  promise: Promise<{ data: ApiResponse<T> }>,
): Promise<T> {
  const { data } = await promise;
  if (!data.success) {
    throw new Error(cleanErrorMessage(data.msg ?? 'Request failed'));
  }
  return data.data;
}

export async function unwrapPage<T>(
  promise: Promise<{ data: ApiResponse<PageModel<T>> }>,
): Promise<PageModel<T>> {
  return unwrap(promise);
}

export default client;
