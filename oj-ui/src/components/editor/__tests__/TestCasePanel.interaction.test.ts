import { describe, expect, it } from 'vitest'

import type { TestCase } from '@/types/problem'
import {
  resolveFirstFailedTabIndex,
  resolveNextActiveTab,
  summarizeTestCaseResults,
} from '../TestCasePanel.vue'

const makeCase = (passed?: boolean): TestCase => ({
  input: 'input',
  expectedOutput: 'output',
  ...(passed === undefined
    ? {}
    : {
        result: {
          passed,
          actualOutput: passed ? 'ok' : 'wrong',
          error: passed ? null : 'mismatch',
        },
      }),
})

describe('test case panel interaction helpers', () => {
  it('finds the first failed test index', () => {
    const testCases = [makeCase(true), makeCase(false), makeCase(false)]
    expect(resolveFirstFailedTabIndex(testCases)).toBe(1)
  })

  it('returns null when there is no failed test', () => {
    const testCases = [makeCase(true), makeCase(), makeCase(true)]
    expect(resolveFirstFailedTabIndex(testCases)).toBeNull()
  })

  it('switches active tab to first failed test when results include failures', () => {
    const testCases = [makeCase(true), makeCase(false), makeCase(true)]
    expect(resolveNextActiveTab(testCases, 2)).toBe(1)
  })

  it('keeps active tab when all available results pass', () => {
    const testCases = [makeCase(true), makeCase(true), makeCase()]
    expect(resolveNextActiveTab(testCases, 1)).toBe(1)
  })

  it('clamps active tab when it is out of range', () => {
    const testCases = [makeCase(), makeCase()]
    expect(resolveNextActiveTab(testCases, 8)).toBe(1)
  })

  it('summarizes passed failed and pending counts', () => {
    const summary = summarizeTestCaseResults([makeCase(true), makeCase(false), makeCase()])

    expect(summary).toEqual({
      total: 3,
      passed: 1,
      failed: 1,
      pending: 1,
      hasResult: true,
    })
  })
})
