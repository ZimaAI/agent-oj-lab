import { useToast } from '@/composables/useToast'
import { getApiBaseUrl } from '@/utils/apiBaseUrl'

// HTTP client configuration
const API_BASE_URL = getApiBaseUrl()

interface RequestOptions extends RequestInit {
  params?: Record<string, any>
  silent?: boolean // 是否静默错误（不显示 toast）
}

interface Result<T> {
  code: string
  message: string
  data: T
}

class HttpClient {
  private baseURL: string

  constructor(baseURL: string) {
    this.baseURL = baseURL
  }

  private getAuthHeaders(): HeadersInit {
    const token = localStorage.getItem('access_token')
    return {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    }
  }

  private buildURL(path: string, params?: Record<string, any>): string {
    const url = new URL(path, this.baseURL)
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          url.searchParams.append(key, String(value))
        }
      })
    }
    return url.toString()
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const { params, silent = false, ...fetchOptions } = options
    const url = this.buildURL(path, params)

    try {
      const response = await fetch(url, {
        ...fetchOptions,
        headers: {
          ...this.getAuthHeaders(),
          ...fetchOptions.headers,
        },
      })

      if (!response.ok) {
        const error = await response.json().catch(() => ({ message: 'Request failed' }))
        const errorMessage = error.message || `HTTP ${response.status}`

        // Handle 401 Unauthorized (token expiration)
        if (response.status === 401) {
          // Clear auth state
          localStorage.removeItem('access_token')
          localStorage.removeItem('refresh_token')

          // Save current URL for return after login
          const currentPath = window.location.pathname + window.location.search
          if (currentPath !== '/login') {
            localStorage.setItem('return_url', currentPath)
          }

          if (!silent) {
            const { error: showError } = useToast()
            showError('登录已过期，请重新登录', 3000)
          }

          // Redirect to login
          setTimeout(() => {
            window.location.href = '/login'
          }, 1000)
        } else if (!silent) {
          const { error: showError } = useToast()
          if (response.status === 404) {
            showError('请求的资源不存在', 3000)
          } else if (response.status === 403) {
            if (errorMessage === '体验次数已用完') {
              showError('体验次数已用完，请联系管理员', 5000)
            } else {
              showError('没有权限访问该资源', 3000)
            }
          } else {
            showError(errorMessage, 3000)
          }
        }

        // Create error with status code
        const err: any = new Error(errorMessage)
        err.status = response.status
        throw err
      }

      const result: Result<T> = await response.json()

      // 检查业务状态码
      if (result.code !== '0') {
        const errorMessage = result.message || '请求失败'

        if (!silent) {
          const { error: showError } = useToast()
          showError(errorMessage, 3000)
        }

        throw new Error(errorMessage)
      }

      return result.data
    } catch (err) {
      // 网络错误或其他异常
      if (err instanceof Error && err.message.includes('Failed to fetch')) {
        const errorMessage = '网络连接失败，请检查网络设置'
        if (!silent) {
          const { error: showError } = useToast()
          showError(errorMessage, 3000)
        }
        throw new Error(errorMessage)
      }
      throw err
    }
  }

  get<T>(path: string, params?: Record<string, any>): Promise<T> {
    return this.request<T>(path, { method: 'GET', params })
  }

  post<T>(path: string, data?: any): Promise<T> {
    return this.request<T>(path, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  put<T>(path: string, data?: any): Promise<T> {
    return this.request<T>(path, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  }

  delete<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: 'DELETE' })
  }
}

export const http = new HttpClient(API_BASE_URL)
