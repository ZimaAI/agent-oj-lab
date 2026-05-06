import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { login, refreshToken, getCurrentUser, storage } = vi.hoisted(() => {
  const storage = new Map<string, string>()

  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => {
        storage.set(key, value)
      },
      removeItem: (key: string) => {
        storage.delete(key)
      },
      clear: () => {
        storage.clear()
      },
    },
    writable: true,
    configurable: true,
  })

  return {
    login: vi.fn(),
    refreshToken: vi.fn(),
    getCurrentUser: vi.fn(),
    storage,
  }
})

vi.mock('@/api/auth', () => ({
  authApi: {
    login,
    refreshToken,
  },
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getCurrentUser,
  },
}))

import { useAuthStore } from '../auth'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    storage.clear()
    vi.clearAllMocks()
  })

  it('stores backend login userInfo fields and loads the current user trial count after login', async () => {
    login.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userInfo: {
        userId: 7,
        username: 'demo',
        userType: 1,
        permissions: ['conversation:read'],
      },
    })
    getCurrentUser.mockResolvedValue({
      id: 7,
      username: 'demo',
      userType: 1,
      isActive: 1,
      validFrom: null,
      validUntil: null,
      trialCount: 9,
    })

    const store = useAuthStore()
    await store.login({ username: 'demo' })

    expect(store.accessToken).toBe('access-token')
    expect(store.refreshToken).toBe('refresh-token')
    expect(store.userInfo).toEqual({
      userId: 7,
      username: 'demo',
      userType: 1,
      permissions: ['conversation:read'],
    })
    expect(store.currentUser).toEqual({
      id: 7,
      username: 'demo',
      userType: 1,
      isActive: 1,
      validFrom: null,
      validUntil: null,
      trialCount: 9,
    })
    expect(store.trialCount).toBe(9)
    expect(localStorage.getItem('access_token')).toBe('access-token')
    expect(localStorage.getItem('refresh_token')).toBe('refresh-token')
  })

  it('stores backend refresh userInfo fields when refreshing tokens', async () => {
    refreshToken.mockResolvedValue({
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      userInfo: {
        userId: 11,
        username: 'refreshed-user',
        userType: 0,
        permissions: [],
      },
    })

    const store = useAuthStore()
    store.refreshToken = 'existing-refresh-token'

    await store.refresh()

    expect(refreshToken).toHaveBeenCalledWith({ refreshToken: 'existing-refresh-token' })
    expect(store.accessToken).toBe('new-access-token')
    expect(store.refreshToken).toBe('new-refresh-token')
    expect(store.userInfo?.userId).toBe(11)
  })

  it('checks permissions from the backend userInfo payload', () => {
    const store = useAuthStore()
    store.userInfo = {
      userId: 15,
      username: 'permission-user',
      userType: 0,
      permissions: ['conversation:delete'],
    }

    expect(store.hasPermission('conversation:delete')).toBe(true)
    expect(store.hasPermission('conversation:update')).toBe(false)
  })

  it('clears tokens and backend user state on logout', () => {
    localStorage.setItem('access_token', 'access-token')
    localStorage.setItem('refresh_token', 'refresh-token')

    const store = useAuthStore()
    store.accessToken = 'access-token'
    store.refreshToken = 'refresh-token'
    store.userInfo = {
      userId: 1,
      username: 'demo',
      userType: 0,
      permissions: [],
    }
    store.currentUser = {
      id: 1,
      username: 'demo',
      userType: 0,
      isActive: 1,
      validFrom: null,
      validUntil: null,
      trialCount: 3,
    }

    store.logout()

    expect(store.accessToken).toBeNull()
    expect(store.refreshToken).toBeNull()
    expect(store.userInfo).toBeNull()
    expect(store.currentUser).toBeNull()
    expect(store.trialCount).toBeNull()
    expect(localStorage.getItem('access_token')).toBeNull()
    expect(localStorage.getItem('refresh_token')).toBeNull()
  })
})
