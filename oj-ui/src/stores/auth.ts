import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import type { CurrentUserResponse, LoginRequest, UserInfo } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('access_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refresh_token'))
  const userInfo = ref<UserInfo | null>(null)
  const currentUser = ref<CurrentUserResponse | null>(null)

  // 仅通过 access token 判断当前登录态。
  const isAuthenticated = computed(() => !!accessToken.value)

  // 当前用户体验次数从 current user 接口读取。
  const trialCount = computed(() => currentUser.value?.trialCount ?? null)

  // 权限判断基于登录接口返回的 userInfo。
  const hasPermission = (permission: string) => {
    return userInfo.value?.permissions.includes(permission) ?? false
  }

  async function fetchCurrentUser() {
    try {
      currentUser.value = await userApi.getCurrentUser()
    } catch {
      // 静默处理，不影响正常使用
    }
  }

  async function login(credentials: LoginRequest) {
    const response = await authApi.login(credentials)
    accessToken.value = response.accessToken
    refreshToken.value = response.refreshToken
    userInfo.value = response.userInfo

    localStorage.setItem('access_token', response.accessToken)
    localStorage.setItem('refresh_token', response.refreshToken)

    // 登录后获取当前用户详情（含 trialCount）。
    await fetchCurrentUser()
  }

  async function refresh() {
    if (!refreshToken.value) throw new Error('No refresh token')

    const response = await authApi.refreshToken({ refreshToken: refreshToken.value })
    accessToken.value = response.accessToken
    refreshToken.value = response.refreshToken
    userInfo.value = response.userInfo

    localStorage.setItem('access_token', response.accessToken)
    localStorage.setItem('refresh_token', response.refreshToken)
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    userInfo.value = null
    currentUser.value = null

    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    currentUser,
    trialCount,
    isAuthenticated,
    hasPermission,
    login,
    refresh,
    logout,
    fetchCurrentUser,
  }
})
