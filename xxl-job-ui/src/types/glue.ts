export interface XxlJobLogGlue {
  id: number;
  jobId: number;
  glueType: string;
  glueSource: string;
  glueRemark: string;
  addTime: string;
  updateTime: string;
}

export interface GlueCodeData {
  jobInfo: import('./job').XxlJobInfo;
  jobLogGlues: XxlJobLogGlue[];
  GlueTypeEnum: Record<string, string>;
}
