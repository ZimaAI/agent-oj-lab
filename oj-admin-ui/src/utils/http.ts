import type { ApiResult, RequestOptions } from '@/types/http'
import { getApiBaseUrl } from '@/utils/apiBaseUrl'

const API_BASE_URL = getApiBaseUrl()
const ACCESS_TOKEN_KEY = 'admin_access_token'
const REFRESH_TOKEN_KEY = 'admin_refresh_token'
const USER_INFO_KEY = 'admin_user_info'

interface TokenRefreshPayload {
  accessToken: string
  refreshToken: string
  userInfo?: unknown
}

const clearAuthStorage = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_INFO_KEY)
  window.dispatchEvent(new Event('admin-auth-cleared'))
}

class HttpClient {
  private readonly baseUrl: string
  private refreshPromise: Promise<string | null> | null = null

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl
  }

  private getAuthHeaders(): HeadersInit {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    return {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    }
  }

  // Keep browser-generated multipart boundaries for form uploads.
  private isFormDataPayload(data: unknown): data is FormData {
    return typeof FormData !== 'undefined' && data instanceof FormData
  }

  // Build write options by payload type to avoid FormData JSON serialization.
  private buildWriteOptions(method: 'POST' | 'PUT', data?: unknown): RequestOptions {
    if (this.isFormDataPayload(data)) {
      return {
        method,
        body: data,
      }
    }
    return {
      method,
      headers: {
        'Content-Type': 'application/json',
      },
      body: data === undefined ? undefined : JSON.stringify(data),
    }
  }

  private buildUrl(path: string, params?: RequestOptions['params']) {
    const url = new URL(path, this.baseUrl)
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          url.searchParams.append(key, String(value))
        }
      })
    }
    return url.toString()
  }

  private isAuthPath(path: string) {
    return path.startsWith('/api/auth/')
  }

  private redirectToLogin() {
    if (window.location.pathname === '/login') {
      return
    }
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
  }

  private async refreshAccessToken(): Promise<string | null> {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (!refreshToken) {
      return null
    }

    if (this.refreshPromise) {
      return this.refreshPromise
    }

    this.refreshPromise = (async () => {
      try {
        const response = await fetch(this.buildUrl('/api/auth/refresh'), {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ refreshToken }),
        })

        if (!response.ok) {
          return null
        }

        const body = await response.json().catch(() => ({}))
        const result = body as ApiResult<TokenRefreshPayload>
        if (result.code !== '0' || !result.data?.accessToken || !result.data?.refreshToken) {
          return null
        }

        localStorage.setItem(ACCESS_TOKEN_KEY, result.data.accessToken)
        localStorage.setItem(REFRESH_TOKEN_KEY, result.data.refreshToken)
        if (result.data.userInfo) {
          localStorage.setItem(USER_INFO_KEY, JSON.stringify(result.data.userInfo))
        }
        window.dispatchEvent(new Event('admin-auth-updated'))
        return result.data.accessToken
      } catch {
        return null
      } finally {
        this.refreshPromise = null
      }
    })()

    return this.refreshPromise
  }

  private async requestInternal<T>(path: string, options: RequestOptions = {}, allowRetry = true): Promise<T> {
    const { params, ...rest } = options
    const url = this.buildUrl(path, params)

    const response = await fetch(url, {
      ...rest,
      headers: {
        ...this.getAuthHeaders(),
        ...rest.headers,
      },
    })

    const body = await response.json().catch(() => ({}))

    if (!response.ok) {
      if (response.status === 401 && allowRetry && !this.isAuthPath(path)) {
        const refreshedAccessToken = await this.refreshAccessToken()
        if (refreshedAccessToken) {
          return this.requestInternal<T>(path, options, false)
        }
      }

      if (response.status === 401) {
        clearAuthStorage()
        if (!this.isAuthPath(path)) {
          this.redirectToLogin()
        }
      }
      throw new Error(body.message || `HTTP ${response.status}`)
    }

    const result = body as ApiResult<T>
    if (result.code !== '0') {
      throw new Error(result.message || '请求失败')
    }

    return result.data
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    return this.requestInternal(path, options)
  }

  get<T>(path: string, params?: RequestOptions['params']) {
    return this.request<T>(path, { method: 'GET', params })
  }

  post<T>(path: string, data?: unknown) {
    return this.request<T>(path, this.buildWriteOptions('POST', data))
  }

  put<T>(path: string, data?: unknown) {
    return this.request<T>(path, this.buildWriteOptions('PUT', data))
  }
}

export const http = new HttpClient(API_BASE_URL)
