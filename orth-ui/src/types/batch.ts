export interface SubTaskConfig {
  executorParam?: string;
  jobDesc?: string;
  author?: string;
  scheduleConf?: string;
  scheduleType?: string;
  alarmEmail?: string;
}

export interface BatchCopyRequest {
  templateJobId: number;
  mode: 'simple' | 'advanced';
  params?: string[];
  nameTemplate?: string;
  jobDesc?: string;
  author?: string;
  scheduleConf?: string;
  scheduleType?: string;
  alarmEmail?: string;
  tasks?: SubTaskConfig[];
}

export interface BatchCopyResult {
  successCount: number;
  failCount: number;
  createdJobIds: number[];
  errors: string[];
}
