import { describe, expect, it } from 'vitest'

import {
  normalizeExecutionResultsPayload,
  normalizeTestResultsValue,
} from '../testCaseResultNormalizer'

describe('testCaseResultNormalizer', () => {
  it('normalizes legacy passed/output/error execution payloads', () => {
    expect(
      normalizeExecutionResultsPayload({
        results: [
          {
            passed: true,
            output: 123,
            error: null,
          },
        ],
      }),
    ).toEqual([
      {
        success: true,
        result: '123',
        errorMessage: null,
      },
    ])
  })

  it('normalizes success/result/errorMessage execution payloads', () => {
    expect(
      normalizeExecutionResultsPayload({
        results: [
          {
            success: false,
            result: '',
            errorMessage: 'runtime error',
          },
        ],
      }),
    ).toEqual([
      {
        success: false,
        result: '',
        errorMessage: 'runtime error',
      },
    ])
  })

  it('returns an empty list when execution payload does not contain result array', () => {
    expect(normalizeExecutionResultsPayload({})).toEqual([])
    expect(normalizeExecutionResultsPayload(null)).toEqual([])
  })

  it('normalizes stringified submission detail test results', () => {
    expect(
      normalizeTestResultsValue(
        '{"success":true,"results":[{"passed":true,"output":"ok","error":null}]}',
      ),
    ).toEqual([
      {
        success: true,
        result: 'ok',
        errorMessage: null,
      },
    ])
  })

  it('returns an empty list for invalid test result payloads', () => {
    expect(normalizeTestResultsValue('not-json')).toEqual([])
    expect(normalizeTestResultsValue('')).toEqual([])
  })
})
