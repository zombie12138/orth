export interface ApiResponse<T> {
  code: number;
  msg: string | null;
  data: T;
  success: boolean;
}

export interface PageModel<T> {
  data: T[];
  total: number;
}
