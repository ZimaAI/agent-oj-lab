import { beforeEach, describe, expect, it, vi } from 'vitest'

const { httpGet, httpPut } = vi.hoisted(() => ({
  httpGet: vi.fn(),
  httpPut: vi.fn(),
}))

vi.mock('@/utils/http', () => ({
  http: {
    get: httpGet,
    put: httpPut,
  },
}))

import { adminUserApi } from '../adminUser'

describe('admin user api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses default DISABLED status when disabling user', () => {
    adminUserApi.disableUser(12)

    expect(httpPut).toHaveBeenCalledWith('/api/admin/users/12/status', {
      status: 'DISABLED',
    })
  })

  it('requests login logs page from login-logs endpoint', () => {
    adminUserApi.pageLoginLogs({
      current: 3,
      size: 20,
    })

    expect(httpGet).toHaveBeenCalledWith('/api/admin/users/login-logs', {
      current: 3,
      size: 20,
    })
  })

  it('requests user page data from users endpoint', () => {
    adminUserApi.pageUsers({
      current: 2,
      size: 15,
    })

    expect(httpGet).toHaveBeenCalledWith('/api/admin/users', {
      current: 2,
      size: 15,
    })
  })
})
