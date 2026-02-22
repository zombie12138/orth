export interface JobUser {
  id: number;
  username: string;
  password: string;
  role: number;
  permission: string;
}

export interface UserQueryParams {
  offset: number;
  pagesize: number;
  username?: string;
  role?: number;
}
