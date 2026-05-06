import { describe, expect, it } from 'vitest'

import { getErrorMessage, getErrorStatus } from '../errorUtils'

describe('errorUtils', () => {
  it('returns the original error message when value is an Error instance', () => {
    expect(getErrorMessage(new Error('network failed'), 'fallback')).toBe('network failed')
  })

  it('falls back to default message when value is not an Error instance', () => {
    expect(getErrorMessage({ message: 'ignored' }, 'fallback')).toBe('fallback')
  })

  it('extracts numeric status from an error-like object', () => {
    expect(getErrorStatus({ status: 404 })).toBe(404)
  })

  it('returns undefined when status is missing or not numeric', () => {
    expect(getErrorStatus({ status: '404' })).toBeUndefined()
    expect(getErrorStatus(null)).toBeUndefined()
  })
})
