import { describe, expect, it } from 'vitest'

import { resolveEditorCacheKey } from '../useCodeEditor'

describe('useCodeEditor cache key', () => {
  it('uses problem id + language as editor storage key', () => {
    expect(resolveEditorCacheKey(101, 'python')).toBe('code_101_python')
    expect(resolveEditorCacheKey(101, 'java')).toBe('code_101_java')
    expect(resolveEditorCacheKey(101, 'javascript')).toBe('code_101_javascript')
  })
})
