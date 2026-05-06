import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import type { AdminLoginRequest, AdminUserInfo } from '@/types/auth'

const ACCESS_TOKEN_KEY = 'admin_access_token'
const REFRESH_TOKEN_KEY = 'admin_refresh_token'
const USER_INFO_KEY = 'admin_user_info'

const getStorageItem = (key: string) => {
  if (typeof window === 'undefined') {
    return null
  }
  return localStorage.getItem(key)
}

const setStorageItem = (key: string, value: string) => {
  if (typeof window === 'undefined') {
    return
  }
  localStorage.setItem(key, value)
}

const removeStorageItem = (key: string) => {
  if (typeof window === 'undefined') {
    return
  }
  localStorage.removeItem(key)
}

const parseUserInfo = (): AdminUserInfo | null => {
  const raw = getStorageItem(USER_INFO_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as AdminUserInfo
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('admin-auth', () => {
  const accessToken = ref<string | null>(getStorageItem(ACCESS_TOKEN_KEY))
  const refreshToken = ref<string | null>(getStorageItem(REFRESH_TOKEN_KEY))
  const userInfo = ref<AdminUserInfo | null>(parseUserInfo())

  const isAuthenticated = computed(() => !!accessToken.value)

  const hasPermission = (permission: string) => {
    return userInfo.value?.permissions.includes(permission) ?? false
  }

  const syncTokensFromStorage = () => {
    accessToken.value = getStorageItem(ACCESS_TOKEN_KEY)
    refreshToken.value = getStorageItem(REFRESH_TOKEN_KEY)
    userInfo.value = parseUserInfo()
  }

  const clearState = () => {
    accessToken.value = null
    refreshToken.value = null
    userInfo.value = null
  }

  async function login(request: AdminLoginRequest) {
    const data = await authApi.login(request)
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    userInfo.value = data.userInfo
    setStorageItem(ACCESS_TOKEN_KEY, data.accessToken)
    setStorageItem(REFRESH_TOKEN_KEY, data.refreshToken)
    setStorageItem(USER_INFO_KEY, JSON.stringify(data.userInfo))
  }

  function logout() {
    clearState()
    removeStorageItem(ACCESS_TOKEN_KEY)
    removeStorageItem(REFRESH_TOKEN_KEY)
    removeStorageItem(USER_INFO_KEY)
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new Event('admin-auth-cleared'))
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('admin-auth-updated', syncTokensFromStorage)
    window.addEventListener('admin-auth-cleared', clearState)
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    isAuthenticated,
    hasPermission,
    login,
    logout,
  }
})
