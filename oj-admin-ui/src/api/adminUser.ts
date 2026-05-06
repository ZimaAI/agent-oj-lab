import { http } from '@/utils/http'

export interface AdminUserStatusUpdateRequest {
  status: 'DISABLED'
}

export interface AdminLoginLogItem {
  id: number
  userId: number
  username: string
  loginIp: string
  loginTime: string
}

export interface AdminLoginLogPage {
  current: number
  size: number
  total: number
  records: AdminLoginLogItem[]
}

export interface AdminLoginLogQueryParams extends Record<string, string | number | boolean | null | undefined> {
  current?: number
  size?: number
}

export interface AdminUserItem {
  id: number
  username: string
  userType: number
  needPassword: number
  isActive: number
  validFrom: string | null
  validUntil: string | null
  trialCount: number | null
  createTime: string | null
  updateTime: string | null
}

export interface AdminUserPage {
  current: number
  size: number
  total: number
  records: AdminUserItem[]
}

export interface AdminUserPageQueryParams extends Record<string, string | number | boolean | null | undefined> {
  current?: number
  size?: number
}

export const adminUserApi = {
  disableUser(userId: number, data: AdminUserStatusUpdateRequest = { status: 'DISABLED' }) {
    return http.put<void>(`/api/admin/users/${userId}/status`, data)
  },

  pageLoginLogs(params: AdminLoginLogQueryParams) {
    return http.get<AdminLoginLogPage>('/api/admin/users/login-logs', params)
  },

  pageUsers(params: AdminUserPageQueryParams) {
    return http.get<AdminUserPage>('/api/admin/users', params)
  },
}
