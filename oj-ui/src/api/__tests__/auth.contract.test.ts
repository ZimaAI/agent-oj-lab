import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: {
    post: vi.fn(),
    request: vi.fn(),
  },
}))

vi.mock('@/utils/http', () => ({
  http,
}))

import { authApi } from '../auth'
import { userApi } from '../user'

describe('auth api contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('posts login requests to the backend auth endpoint and returns userInfo payloads', async () => {
    http.post.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userInfo: {
        userId: 1,
        username: 'demo',
        userType: 0,
        permissions: ['question:read'],
      },
    })

    const result = await authApi.login({ username: 'demo' })

    expect(http.post).toHaveBeenCalledWith('/api/auth/login', { username: 'demo' })
    expect(result.userInfo).toEqual({
      userId: 1,
      username: 'demo',
      userType: 0,
      permissions: ['question:read'],
    })
  })

  it('posts refresh requests to the backend refresh endpoint', async () => {
    http.post.mockResolvedValue({
      accessToken: 'next-access-token',
      refreshToken: 'next-refresh-token',
      userInfo: {
        userId: 2,
        username: 'refresh-user',
        userType: 1,
        permissions: [],
      },
    })

    const result = await authApi.refreshToken({ refreshToken: 'refresh-token' })

    expect(http.post).toHaveBeenCalledWith('/api/auth/refresh', {
      refreshToken: 'refresh-token',
    })
    expect(result.userInfo.userId).toBe(2)
  })

  it('requests the current user from the backend users endpoint', async () => {
    http.request.mockResolvedValue({
      id: 3,
      username: 'current-user',
      userType: 0,
      isActive: 1,
      validFrom: null,
      validUntil: null,
      trialCount: 5,
    })

    const result = await userApi.getCurrentUser()

    expect(http.request).toHaveBeenCalledWith('/api/users/current', {
      method: 'GET',
      silent: true,
    })
    expect(result.trialCount).toBe(5)
  })
})
