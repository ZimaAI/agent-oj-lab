import { describe, expect, it } from 'vitest'

import { resolveApiBaseUrl } from '../apiBaseUrl'

describe('resolveApiBaseUrl', () => {
  it('uses the explicit VITE_API_BASE_URL when provided', () => {
    expect(resolveApiBaseUrl('https://api.example.com', 'https://ui.example.com')).toBe('https://api.example.com')
  })

  it('falls back to the current browser origin when no explicit value exists', () => {
    expect(resolveApiBaseUrl('', 'https://ui.example.com')).toBe('https://ui.example.com')
  })

  it('falls back to localhost backend when no browser origin exists', () => {
    expect(resolveApiBaseUrl('', undefined)).toBe('http://localhost:8080')
  })
})
