import client, { unwrap, unwrapPage } from './client';
import type { JobLog, LogQueryParams, LogResult } from '../types/log';

export function fetchLogs(params: LogQueryParams) {
  return unwrapPage<JobLog>(client.get('/api/v1/logs', { params }));
}

export function fetchLogContent(id: number, fromLineNum: number) {
  return unwrap<LogResult>(
    client.get(`/api/v1/logs/${id}/content`, {
      params: { fromLineNum },
    }),
  );
}

export function killJob(id: number) {
  return unwrap<string>(client.post(`/api/v1/logs/${id}/kill`));
}

export function clearLogs(jobGroup: number, jobId: number, type: number) {
  return unwrap<string>(
    client.post('/api/v1/logs/clear', null, {
      params: { jobGroup, jobId, type },
    }),
  );
}
