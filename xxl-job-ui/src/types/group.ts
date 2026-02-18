export interface XxlJobGroup {
  id: number;
  appname: string;
  title: string;
  addressType: number;
  addressList: string;
  updateTime: string;
  registryList: string[];
}

export interface GroupQueryParams {
  offset: number;
  pagesize: number;
  appname?: string;
  title?: string;
}
