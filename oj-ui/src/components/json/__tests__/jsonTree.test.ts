import { describe, expect, it } from 'vitest'

import {
  buildDefaultExpandedPaths,
  describeCollapsibleValue,
  normalizeNestedJsonValue,
  parseJsonLikeValue,
  toInlineString,
} from '../jsonTree'

describe('json tree utilities', () => {
  it('parses valid json string and falls back to text for invalid string', () => {
    expect(parseJsonLikeValue('{"a":1}')).toEqual({ kind: 'json', value: { a: 1 } })
    expect(parseJsonLikeValue('{a:1}')).toEqual({ kind: 'text', value: '{a:1}' })
  })

  it('handles empty and object inputs', () => {
    expect(parseJsonLikeValue(undefined)).toEqual({ kind: 'empty', value: null })
    expect(parseJsonLikeValue(null)).toEqual({ kind: 'empty', value: null })
    expect(parseJsonLikeValue('   ')).toEqual({ kind: 'empty', value: null })
    expect(parseJsonLikeValue({ a: 1 })).toEqual({ kind: 'json', value: { a: 1 } })
  })

  it('recursively parses nested json strings for deep tree expansion', () => {
    expect(
      parseJsonLikeValue('{"outer":"{\\"inner\\":{\\"leaf\\":[1,2,3]}}","raw":"plain"}'),
    ).toEqual({
      kind: 'json',
      value: {
        outer: {
          inner: {
            leaf: [1, 2, 3],
          },
        },
        raw: 'plain',
      },
    })

    expect(
      normalizeNestedJsonValue({
        nestedArray: '[{"id":1},{"id":2}]',
      }),
    ).toEqual({
      nestedArray: [{ id: 1 }, { id: 2 }],
    })
  })

  it('collects default expanded paths for nested objects and arrays', () => {
    const value = {
      profile: {
        tags: ['x', { deep: true }],
      },
      list: [{ child: 1 }],
    }

    expect(buildDefaultExpandedPaths(value)).toEqual([
      '$',
      '$["profile"]',
      '$["profile"]["tags"]',
      '$["profile"]["tags"][1]',
      '$["list"]',
      '$["list"][0]',
    ])
  })

  it('uses collision-safe key paths for dotted keys', () => {
    const dottedKey = { 'a.b': {} }
    const nestedKey = { a: { b: {} } }

    expect(buildDefaultExpandedPaths(dottedKey)).toEqual(['$', '$["a.b"]'])
    expect(buildDefaultExpandedPaths(nestedKey)).toEqual(['$', '$["a"]', '$["a"]["b"]'])
  })

  it('keeps shared-reference paths across different branches', () => {
    const shared = { leaf: {} }
    const value = {
      a: shared,
      b: shared,
    }

    expect(buildDefaultExpandedPaths(value)).toEqual([
      '$',
      '$["a"]',
      '$["a"]["leaf"]',
      '$["b"]',
      '$["b"]["leaf"]',
    ])
  })

  it('skips revisited nodes to avoid cycle recursion errors', () => {
    const cyclic: Record<string, unknown> = { level: {} }
    cyclic.self = cyclic
    ;(cyclic.level as Record<string, unknown>).back = cyclic

    expect(() => buildDefaultExpandedPaths(cyclic)).not.toThrow()
    expect(buildDefaultExpandedPaths(cyclic)).toEqual(['$', '$["level"]'])
  })

  it('describes collapsible values and keeps truncation threshold behavior', () => {
    expect(describeCollapsibleValue({ a: 1, b: 2 })).toBe('{2 keys}')
    expect(describeCollapsibleValue([1, 2, 3])).toBe('[3 items]')
    expect(describeCollapsibleValue('abc')).toBe('-')

    expect(toInlineString('1234567890')).toBe('1234567890')
    expect(toInlineString('12345678901')).toBe('1234567890...')
  })
})
