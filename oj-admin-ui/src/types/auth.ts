export interface AdminLoginRequest {
  username: string
  password?: string
}

export interface AdminUserInfo {
  userId: number
  username: string
  userType: number
  permissions: string[]
}

export interface AdminLoginResponse {
  accessToken: string
  refreshToken: string
  userInfo: AdminUserInfo
}

export interface RefreshTokenRequest {
  refreshToken: string
}
