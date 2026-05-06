import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/api/problem', () => ({
  problemApi: {
    getProblemById: vi.fn(),
    executeCode: vi.fn(),
  },
}))

import { problemApi } from '@/api/problem'
import { useProblemDetail } from '../useProblemDetail'
import { Difficulty } from '@/types/problem'

describe('useProblemDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads problem detail from shared backend contract', async () => {
    vi.mocked(problemApi.getProblemById).mockResolvedValue({
      id: 7,
      title: 'two sum',
      description: 'given nums and target',
      difficulty: Difficulty.EASY,
      sharedFunctionName: 'twoSum',
      sharedCodeSkeleton: 'FUNCTION two_sum(nums, target):\n  // TODO',
      sharedTestCases: [
        {
          input: { nums: [2, 7, 11, 15], target: 9 },
          expectedOutput: [0, 1],
          description: 'returns indices',
        },
      ],
      codeTemplates: [
        {
          language: 'PYTHON',
          functionName: 'two_sum',
          codeSkeleton: 'def two_sum(nums, target):\n    pass',
        },
      ],
      conversationId: 'conv-1',
      traceId: 'trace-1',
      agentName: 'agent',
      createTime: '2026-04-07T10:00:00',
      updateTime: '2026-04-07T10:00:00',
      tags: [{ id: 1, tagName: 'array' }],
    })

    const { problem, loadProblem } = useProblemDetail()

    await loadProblem(7)

    expect(problem.value?.sharedFunctionName).toBe('twoSum')
    expect(problem.value?.sharedTestCases).toHaveLength(1)
    expect(problem.value?.codeTemplates[0]?.language).toBe('PYTHON')
  })

  it('parses string sharedTestCases from backend detail responses', async () => {
    vi.mocked(problemApi.getProblemById).mockResolvedValue({
      id: 8,
      title: 'single number',
      description: 'find unique number',
      difficulty: Difficulty.EASY,
      sharedFunctionName: 'singleNumber',
      sharedCodeSkeleton: 'FUNCTION single_number(nums):\n  // TODO',
      sharedTestCases: '[{"input":{"nums":[2,2,1]},"expectedOutput":1,"description":"basic"}]',
      codeTemplates: [
        {
          language: 'PYTHON',
          functionName: 'single_number',
          codeSkeleton: 'def single_number(nums):\n    pass',
        },
      ],
      conversationId: 'conv-2',
      traceId: 'trace-2',
      agentName: 'agent',
      createTime: '2026-04-07T11:00:00',
      updateTime: '2026-04-07T11:00:00',
      tags: [{ id: 2, tagName: 'bit' }],
    })

    const { problem, loadProblem } = useProblemDetail()

    await loadProblem(8)

    expect(problem.value?.sharedTestCases).toEqual([
      {
        input: { nums: [2, 2, 1] },
        expectedOutput: 1,
        description: 'basic',
      },
    ])
  })

  it('runs code with explicit language and question id', async () => {
    vi.mocked(problemApi.executeCode).mockResolvedValue({
      results: [],
    })

    const { runCode, runResults, runLoading } = useProblemDetail()

    await runCode('print(1)', 'PYTHON', 123)

    expect(problemApi.executeCode).toHaveBeenCalledWith('print(1)', 123, 'PYTHON')
    expect(runResults.value).toEqual([])
    expect(runLoading.value).toBe(false)
  })

  it('normalizes execution results from backend passed/output/error shape', async () => {
    vi.mocked(problemApi.executeCode).mockResolvedValue({
      results: [
        {
          passed: true,
          output: '3',
          error: null,
        },
      ],
    } as any)

    const { runCode, runResults } = useProblemDetail()

    await runCode('print(1+2)', 'PYTHON', 123)

    expect(runResults.value).toEqual([
      {
        success: true,
        result: '3',
        errorMessage: null,
      },
    ])
  })
})