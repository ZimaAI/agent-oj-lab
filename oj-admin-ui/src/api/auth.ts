import { http } from '@/utils/http'
import type { AdminLoginRequest, AdminLoginResponse, RefreshTokenRequest } from '@/types/auth'

export const authApi = {
  login(data: AdminLoginRequest) {
    return http.post<AdminLoginResponse>('/api/auth/login', data)
  },

  refreshToken(data: RefreshTokenRequest) {
    return http.post<AdminLoginResponse>('/api/auth/refresh', data)
  },
}
