import client, { unwrap } from './client';
import type { GlueCodeData } from '../types/glue';

export function fetchGlueCode(jobId: number) {
  return unwrap<GlueCodeData>(client.get(`/api/v1/jobs/${jobId}/code`));
}

export function saveGlueCode(
  jobId: number,
  glueSource: string,
  glueRemark: string,
) {
  return unwrap<string>(
    client.put(`/api/v1/jobs/${jobId}/code`, null, {
      params: { glueSource, glueRemark },
    }),
  );
}
