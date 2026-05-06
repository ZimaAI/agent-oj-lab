// Authentication types
export interface LoginRequest {
  username: string
  password?: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userInfo: UserInfo
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface UserInfo {
  userId: number
  username: string
  userType: number
  permissions: string[]
}

export interface CurrentUserResponse {
  id: number
  username: string
  userType: number
  isActive: number
  validFrom: string | null
  validUntil: string | null
  trialCount: number
}
