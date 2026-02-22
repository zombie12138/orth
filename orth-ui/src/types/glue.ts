export interface JobLogGlue {
  id: number;
  jobId: number;
  glueType: string;
  glueSource: string;
  glueRemark: string;
  addTime: string;
  updateTime: string;
}

export interface GlueCodeData {
  jobInfo: import('./job').JobInfo;
  jobLogGlues: JobLogGlue[];
  GlueTypeEnum: Record<string, string>;
}
