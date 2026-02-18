export interface XxlJobLog {
  id: number;
  jobGroup: number;
  jobId: number;
  executorAddress: string;
  executorHandler: string;
  executorParam: string;
  executorShardingParam: string;
  executorFailRetryCount: number;
  triggerTime: string;
  scheduleTime: string | null;
  triggerCode: number;
  triggerMsg: string;
  handleTime: string;
  handleCode: number;
  handleMsg: string;
  alarmStatus: number;
}

export interface LogQueryParams {
  offset: number;
  pagesize: number;
  jobGroup: number;
  jobId?: number;
  logStatus?: number;
  filterTime?: string;
}

export interface LogResult {
  fromLineNum: number;
  toLineNum: number;
  logContent: string;
  isEnd: boolean;
}
