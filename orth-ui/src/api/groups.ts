import client, { unwrap, unwrapPage } from './client';
import type { JobGroup, GroupQueryParams } from '../types/group';

export function fetchGroups(params: GroupQueryParams) {
  return unwrapPage<JobGroup>(
    client.get('/api/v1/executor-groups', { params }),
  );
}

export function fetchPermittedGroups() {
  return unwrap<JobGroup[]>(
    client.get('/api/v1/executor-groups/permitted'),
  );
}

export function fetchGroup(id: number) {
  return unwrap<JobGroup>(client.get(`/api/v1/executor-groups/${id}`));
}

export function createGroup(data: Partial<JobGroup>) {
  return unwrap<string>(client.post('/api/v1/executor-groups', data));
}

export function updateGroup(id: number, data: Partial<JobGroup>) {
  return unwrap<string>(client.put(`/api/v1/executor-groups/${id}`, data));
}

export function deleteGroup(id: number) {
  return unwrap<string>(client.delete(`/api/v1/executor-groups/${id}`));
}
