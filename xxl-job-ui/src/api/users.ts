import client, { unwrap, unwrapPage } from './client';
import type { XxlJobUser, UserQueryParams } from '../types/user';

export function fetchUsers(params: UserQueryParams) {
  return unwrapPage<XxlJobUser>(client.get('/api/v1/users', { params }));
}

export function fetchUser(id: number) {
  return unwrap<XxlJobUser>(client.get(`/api/v1/users/${id}`));
}

export function createUser(data: Partial<XxlJobUser>) {
  return unwrap<string>(client.post('/api/v1/users', data));
}

export function updateUser(id: number, data: Partial<XxlJobUser>) {
  return unwrap<string>(client.put(`/api/v1/users/${id}`, data));
}

export function deleteUser(id: number) {
  return unwrap<string>(client.delete(`/api/v1/users/${id}`));
}
