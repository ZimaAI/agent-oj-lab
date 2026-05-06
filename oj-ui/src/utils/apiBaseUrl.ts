const DEFAULT_API_BASE_URL = 'http://localhost:8080'

export function resolveApiBaseUrl(explicitBaseUrl?: string, browserOrigin?: string): string {
  if (explicitBaseUrl) {
    return explicitBaseUrl
  }
  if (browserOrigin) {
    return browserOrigin
  }
  return DEFAULT_API_BASE_URL
}

export function getApiBaseUrl(): string {
  const browserOrigin = typeof window !== 'undefined' ? window.location.origin : undefined
  return resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL, browserOrigin)
}
