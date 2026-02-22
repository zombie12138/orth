import client, { unwrap } from './client';
import type {
  LoginRequest,
  LoginResponse,
  PasswordUpdateRequest,
  RefreshRequest,
} from '../types/auth';

export function login(data: LoginRequest) {
  return unwrap<LoginResponse>(client.post('/api/v1/auth/login', data));
}

export function refresh(data: RefreshRequest) {
  return unwrap<LoginResponse>(client.post('/api/v1/auth/refresh', data));
}

export function logout() {
  return unwrap<string>(client.post('/api/v1/auth/logout'));
}

export function updatePassword(data: PasswordUpdateRequest) {
  return unwrap<string>(client.put('/api/v1/auth/password', data));
}
