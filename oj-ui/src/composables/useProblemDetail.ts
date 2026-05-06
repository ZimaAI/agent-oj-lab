import { ref } from 'vue'
import { problemApi } from '@/api/problem'
import type { AlgorithmCodeTemplate, AlgorithmQuestion, TestCase, TestCaseResultVO } from '@/types/problem'
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
    codeTemplates?: unknown
    codeSkeleton?: unknown
    sharedCodeSkeleton?: unknown
    sharedFunctionName?: unknown
  }

  const sharedTestCases = normalizeTestCases(raw.sharedTestCases ?? raw.testCases)
  const codeTemplates = normalizeCodeTemplates(raw.codeTemplates)
  const sharedCodeSkeleton = typeof raw.sharedCodeSkeleton === 'string'
    ? raw.sharedCodeSkeleton
    : (typeof raw.codeSkeleton === 'string' ? raw.codeSkeleton : null)

  return {
    ...problem,
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