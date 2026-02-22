export interface JobInfo {
  id: number;
  jobGroup: number;
  jobDesc: string;
  addTime: string;
  updateTime: string;
  author: string;
  alarmEmail: string;
  scheduleType: string;
  scheduleConf: string;
  misfireStrategy: string;
  executorRouteStrategy: string;
  executorHandler: string;
  executorParam: string;
  executorBlockStrategy: string;
  executorTimeout: number;
  executorFailRetryCount: number;
  glueType: string;
  glueSource: string;
  glueRemark: string;
  glueUpdatetime: string;
  childJobId: string;
  superTaskId: number | null;
  superTaskName: string;
  triggerStatus: number;
  triggerLastTime: number;
  triggerNextTime: number;
}

export interface JobQueryParams {
  offset: number;
  pagesize: number;
  jobGroup?: number;
  triggerStatus?: number;
  jobDesc?: string;
  executorHandler?: string;
  author?: string;
  superTaskName?: string;
}
