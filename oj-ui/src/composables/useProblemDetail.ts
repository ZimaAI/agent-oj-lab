import { ref } from 'vue'
import { problemApi } from '@/api/problem'
import type { AlgorithmCodeTemplate, AlgorithmQuestion, StandardCase, TestCase, TestCaseResultVO } from '@/types/problem'
import { getErrorMessage, getErrorStatus } from '@/utils/errorUtils'
import { normalizeExecutionResultsPayload } from '@/utils/testCaseResultNormalizer'

function normalizeTestCases(testCases: unknown): TestCase[] {
  if (Array.isArray(testCases)) {
    return testCases as TestCase[]
  }

  if (typeof testCases === 'string' && testCases.trim()) {
    try {
      const parsed = JSON.parse(testCases)
      return Array.isArray(parsed) ? (parsed as TestCase[]) : []
    } catch {
      return []
    }
  }

  return []
}

function normalizeStandardCasePool(casePool: unknown): StandardCase[] {
  if (Array.isArray(casePool)) {
    const normalized: StandardCase[] = []
    for (const item of casePool) {
      const current = (item ?? {}) as Record<string, unknown>
      const stdin = typeof current.stdin === 'string' ? current.stdin : null
      const expectedStdout = typeof current.expectedStdout === 'string' ? current.expectedStdout : null
      const publicCase = typeof current.publicCase === 'boolean'
        ? current.publicCase
        : (typeof current.isPublic === 'boolean' ? current.isPublic : true)
      if (!stdin || !expectedStdout) {
        continue
      }
      normalized.push({
        stdin,
        expectedStdout,
        publicCase,
        description: typeof current.description === 'string' ? current.description : null,
      })
    }
    return normalized
  }

  if (typeof casePool === 'string' && casePool.trim()) {
    try {
      return normalizeStandardCasePool(JSON.parse(casePool))
    } catch {
      return []
    }
  }

  return []
}

function standardCasesToLegacyTestCases(casePool: StandardCase[]): TestCase[] {
  return casePool.map((item) => ({
    input: item.stdin,
    expectedOutput: item.expectedStdout,
    description: item.description ?? null,
  }))
}

function normalizeCodeTemplates(codeTemplates: unknown): AlgorithmCodeTemplate[] {
  if (!Array.isArray(codeTemplates)) {
    return []
  }
  return codeTemplates
    .map((item) => {
      const current = (item ?? {}) as Record<string, unknown>
      const language = typeof current.language === 'string' ? current.language : ''
      if (!language) {
        return null
      }
      return {
        language,
        functionName: typeof current.functionName === 'string' ? current.functionName : null,
        codeSkeleton: typeof current.codeSkeleton === 'string' ? current.codeSkeleton : null,
      } satisfies AlgorithmCodeTemplate
    })
    .filter((item): item is AlgorithmCodeTemplate => item !== null)
}

function normalizeProblemDetail(problem: AlgorithmQuestion): AlgorithmQuestion {
  const raw = problem as AlgorithmQuestion & {
    testCases?: unknown
    sharedTestCases?: unknown
    standardCasePool?: unknown
    codeTemplates?: unknown
    codeSkeleton?: unknown
    sharedCodeSkeleton?: unknown
    sharedFunctionName?: unknown
  }

  const standardCasePool = normalizeStandardCasePool(raw.standardCasePool)
  const sharedTestCases = standardCasePool.length > 0
    ? standardCasesToLegacyTestCases(standardCasePool)
    : normalizeTestCases(raw.sharedTestCases ?? raw.testCases)
  const codeTemplates = normalizeCodeTemplates(raw.codeTemplates)
  const sharedCodeSkeleton = typeof raw.sharedCodeSkeleton === 'string'
    ? raw.sharedCodeSkeleton
    : (typeof raw.codeSkeleton === 'string' ? raw.codeSkeleton : null)

  return {
    ...problem,
    standardCasePool,
    sharedFunctionName: typeof raw.sharedFunctionName === 'string' ? raw.sharedFunctionName : null,
    sharedCodeSkeleton,
    sharedTestCases,
    codeTemplates,
    // Keep aliases for legacy UI paths while migration is in progress.
    codeSkeleton: sharedCodeSkeleton,
    testCases: sharedTestCases,
  }
}

export function useProblemDetail() {
  const problem = ref<AlgorithmQuestion | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const notFound = ref(false)

  const runResults = ref<TestCaseResultVO[]>([])
  const runLoading = ref(false)
  const runError = ref<string | null>(null)

  const loadProblem = async (id: number) => {
    loading.value = true
    error.value = null
    problem.value = null
    notFound.value = false

    try {
      const response = await problemApi.getProblemById(id)
      problem.value = normalizeProblemDetail(response)
    } catch (e: unknown) {
      if (getErrorStatus(e) === 404) {
        notFound.value = true
        error.value = '题目不存在'
      } else {
        error.value = getErrorMessage(e, '加载题目失败')
      }
      console.error('Failed to load problem:', e)
    } finally {
      loading.value = false
    }
  }

  const runCode = async (code: string, language: string, questionId?: number) => {
    const executableQuestionId = questionId ?? problem.value?.id
    if (!executableQuestionId) return
    runLoading.value = true
    runError.value = null
    runResults.value = []

    try {
      const result = await problemApi.executeCode(code, executableQuestionId, language)
      runResults.value = normalizeExecutionResultsPayload(result)
    } catch (e: unknown) {
      runError.value = getErrorMessage(e, '代码执行失败')
    } finally {
      runLoading.value = false
    }
  }

  const clearProblem = () => {
    problem.value = null
    error.value = null
    notFound.value = false
    runResults.value = []
    runError.value = null
  }

  return {
    problem,
    loading,
    error,
    notFound,
    loadProblem,
    clearProblem,
    runCode,
    runResults,
    runLoading,
    runError,
  }
}
