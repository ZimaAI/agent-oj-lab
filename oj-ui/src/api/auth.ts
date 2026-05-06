import { http } from '@/utils/http'
import type { LoginRequest, LoginResponse, RefreshTokenRequest } from '@/types/auth'

export const authApi = {
  login(data: LoginRequest) {
    return http.post<LoginResponse>('/api/auth/login', data)
  },

  refreshToken(data: RefreshTokenRequest) {
    return http.post<LoginResponse>('/api/auth/refresh', data)
  },
}
