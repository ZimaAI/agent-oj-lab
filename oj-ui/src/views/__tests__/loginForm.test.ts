import { describe, expect, it } from 'vitest'

import {
  buildExperienceLoginRequest,
  getExperienceAccountError,
  resolvePostLoginRedirect,
} from '../loginForm'

describe('loginForm', () => {
  it('requires an experience account', () => {
    expect(getExperienceAccountError('')).toBe('请输入体验账号')
    expect(getExperienceAccountError('   ')).toBe('请输入体验账号')
  })

  it('builds a login request with only the trimmed username field', () => {
    expect(buildExperienceLoginRequest('  demo-account  ')).toEqual({
      username: 'demo-account',
    })
  })

  it('prefers the router redirect query over local storage after login', () => {
    expect(resolvePostLoginRedirect('/spans', '/trace/123')).toBe('/spans')
  })

  it('falls back to the stored return url when no redirect query exists', () => {
    expect(resolvePostLoginRedirect(undefined, '/trace/123')).toBe('/trace/123')
  })

  it('ignores malformed or external router redirects and falls back to a safe stored path', () => {
    expect(resolvePostLoginRedirect('https://evil.example', '/trace/123')).toBe('/trace/123')
    expect(resolvePostLoginRedirect('//evil.example', '/trace/123')).toBe('/trace/123')
    expect(resolvePostLoginRedirect('javascript:alert(1)', '/trace/123')).toBe('/trace/123')
    expect(resolvePostLoginRedirect('problems', '/trace/123')).toBe('/trace/123')
  })

  it('ignores malformed or external stored redirects and falls back to /problems', () => {
    expect(resolvePostLoginRedirect(undefined, 'https://evil.example')).toBe('/problems')
    expect(resolvePostLoginRedirect(undefined, '//evil.example')).toBe('/problems')
    expect(resolvePostLoginRedirect(undefined, 'javascript:alert(1)')).toBe('/problems')
    expect(resolvePostLoginRedirect(undefined, 'problems')).toBe('/problems')
  })

  it('falls back to /problems when no redirect target exists', () => {
    expect(resolvePostLoginRedirect(undefined, null)).toBe('/problems')
  })
})

