import client, { unwrap, unwrapPage } from './client';
import type { XxlJobInfo, JobQueryParams } from '../types/job';
import type { BatchCopyRequest, BatchCopyResult } from '../types/batch';

export function fetchJobs(params: JobQueryParams) {
  return unwrapPage<XxlJobInfo>(client.get('/api/v1/jobs', { params }));
}

export function fetchJob(id: number) {
  return unwrap<XxlJobInfo>(client.get(`/api/v1/jobs/${id}`));
}

export function createJob(data: Partial<XxlJobInfo>) {
  return unwrap<string>(client.post('/api/v1/jobs', data));
}

export function updateJob(id: number, data: Partial<XxlJobInfo>) {
  return unwrap<string>(client.put(`/api/v1/jobs/${id}`, data));
}

export function deleteJob(id: number) {
  return unwrap<string>(client.delete(`/api/v1/jobs/${id}`));
}

export function startJob(id: number) {
  return unwrap<string>(client.post(`/api/v1/jobs/${id}/start`));
}

export function stopJob(id: number) {
  return unwrap<string>(client.post(`/api/v1/jobs/${id}/stop`));
}

export function triggerJob(
  id: number,
  executorParam?: string,
  addressList?: string,
) {
  return unwrap<string>(
    client.post(`/api/v1/jobs/${id}/trigger`, null, {
      params: { executorParam, addressList },
    }),
  );
}

export function triggerBatch(
  id: number,
  startTime: string,
  endTime: string,
  executorParam?: string,
  addressList?: string,
) {
  return unwrap<string>(
    client.post(`/api/v1/jobs/${id}/trigger-batch`, null, {
      params: { startTime, endTime, executorParam, addressList },
    }),
  );
}

export function previewTriggerBatch(
  id: number,
  startTime: string,
  endTime: string,
) {
  return unwrap<string[]>(
    client.get(`/api/v1/jobs/${id}/trigger-batch/preview`, {
      params: { startTime, endTime },
    }),
  );
}

export function exportJobs(ids: number[]) {
  return unwrap<string>(
    client.post('/api/v1/jobs/export', null, {
      params: { ids: ids.join(',') },
    }),
  );
}

export function importJobs(json: string) {
  return unwrap<string>(client.post('/api/v1/jobs/import', JSON.parse(json)));
}

export function nextTriggerTime(scheduleType: string, scheduleConf: string) {
  return unwrap<string[]>(
    client.get('/api/v1/jobs/next-trigger-time', {
      params: { scheduleType, scheduleConf },
    }),
  );
}

export function batchCopy(data: BatchCopyRequest) {
  return unwrap<BatchCopyResult>(client.post('/api/v1/jobs/batch-copy', data));
}

export function searchSuperTask(jobGroup: number, query: string) {
  return unwrap<XxlJobInfo[]>(
    client.get('/api/v1/jobs/search-super-task', {
      params: { jobGroup, query },
    }),
  );
}
