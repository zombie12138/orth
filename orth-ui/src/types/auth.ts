export interface JwtUserInfo {
  userId: number;
  username: string;
  role: number;
  permission: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: JwtUserInfo;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface PasswordUpdateRequest {
  oldPassword: string;
  newPassword: string;
}
