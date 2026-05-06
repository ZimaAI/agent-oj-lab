import type { TestCaseResultVO } from '@/types/problem'

interface BackendExecutionResultItem {
  success?: boolean
  result?: unknown
  errorMessage?: unknown
  passed?: boolean
  output?: unknown
  error?: unknown
}

interface BackendExecutionResultPayload {
  results?: unknown
}

function toNullableString(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null
  }

  return String(value)
}

function normalizeExecutionResultItem(item: BackendExecutionResultItem): TestCaseResultVO {
  if (typeof item.success === 'boolean') {
    return {
      success: item.success,
      result: toNullableString(item.result),
      errorMessage: toNullableString(item.errorMessage),
    }
  }

  return {
    success: Boolean(item.passed),
    result: toNullableString(item.output),
    errorMessage: toNullableString(item.error),
  }
}

function normalizeExecutionResultItems(items: unknown[]): TestCaseResultVO[] {
  return items.map((item) => normalizeExecutionResultItem((item ?? {}) as BackendExecutionResultItem))
}

export function normalizeExecutionResultsPayload(payload: unknown): TestCaseResultVO[] {
  if (!payload || typeof payload !== 'object') {
    return []
  }

  const results = (payload as BackendExecutionResultPayload).results
  return Array.isArray(results) ? normalizeExecutionResultItems(results) : []
}

export function normalizeTestResultsValue(testResults: unknown): TestCaseResultVO[] {
  if (Array.isArray(testResults)) {
    return normalizeExecutionResultItems(testResults)
  }

  if (typeof testResults !== 'string' || !testResults.trim()) {
    return []
  }

  try {
    const parsed = JSON.parse(testResults)
    if (Array.isArray(parsed)) {
      return normalizeExecutionResultItems(parsed)
    }
    return normalizeExecutionResultsPayload(parsed)
  } catch {
    return []
  }
}
