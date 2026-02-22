export interface DashboardStats {
  jobInfoCount: number;
  jobLogCount: number;
  jobLogSuccessCount: number;
  executorCount: number;
}

export interface ChartData {
  triggerDayList: string[];
  triggerDayCountRunningList: number[];
  triggerDayCountSucList: number[];
  triggerDayCountFailList: number[];
}
