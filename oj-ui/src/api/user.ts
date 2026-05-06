import { http } from '@/utils/http'
import type { CurrentUserResponse } from '@/types/auth'

export const userApi = {
  getCurrentUser() {
    return http.request<CurrentUserResponse>('/api/users/current', {
      method: 'GET',
      silent: true,
    })
  },
}
