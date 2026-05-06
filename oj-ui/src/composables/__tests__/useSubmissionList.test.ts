import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

const { problemApi } = vi.hoisted(() => ({
  problemApi: {
    getSubmissionList: vi.fn(),
    getSubmissionDetail: vi.fn(),
  },
}))

vi.mock('@/api/problem', () => ({
  problemApi,
}))

import { useSubmissionList } from '../useSubmissionList'

describe('useSubmissionList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads submissions with the backend list item shape and frontend pagination request aliases', async () => {
    problemApi.getSubmissionList.mockResolvedValue({
      records: [
        {
          id: 9,
          algorithmQuestionId: 101,
          questionTitle: '最长递增子序列',
          passCount: 7,
          totalCount: 10,
          language: 'java',
          createTime: '2026-04-07T10:00:00',
        },
      ],
      total: 1,
      size: 20,
      current: 1,
      pages: 1,
    })

    const problemId = ref<number | undefined>(101)
    const { submissions, total, currentPage, loading, error } = useSubmissionList(problemId)

    await Promise.resolve()
    await Promise.resolve()

    expect(problemApi.getSubmissionList).toHaveBeenCalledWith({
      algorithmQuestionId: 101,
      pageNum: 1,
      pageSize: 20,
    })
    expect(submissions.value).toEqual([
      {
        id: 9,
        algorithmQuestionId: 101,
        questionTitle: '最长递增子序列',
        passCount: 7,
        totalCount: 10,
        language: 'java',
        createTime: '2026-04-07T10:00:00',
      },
    ])
    expect(total.value).toBe(1)
    expect(currentPage.value).toBe(1)
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('loads submission detail with the backend detail shape including language and evaluation', async () => {
    problemApi.getSubmissionList.mockResolvedValue({
      records: [
        {
          id: 12,
          algorithmQuestionId: 101,
          questionTitle: '接雨水',
          passCount: 3,
          totalCount: 5,
          language: 'python',
          createTime: '2026-04-07T11:00:00',
        },
      ],
      total: 1,
      size: 20,
      current: 1,
      pages: 1,
    })
    problemApi.getSubmissionDetail.mockResolvedValue({
      id: 12,
      algorithmQuestionId: 101,
      questionTitle: '接雨水',
      code: 'print(1)',
      language: 'python',
      testResults: [
        {
          success: true,
          result: '1',
          errorMessage: null,
        },
      ],
      passCount: 3,
      totalCount: 5,
      codeEvaluation: '通过大部分测试用例',
      createTime: '2026-04-07T11:00:00',
    })


    const problemId = ref<number | undefined>(101)
    const { toggleExpand, expandedId, detailData, detailLoading } = useSubmissionList(problemId)

    await Promise.resolve()
    await Promise.resolve()
    await toggleExpand(12)

    expect(problemApi.getSubmissionDetail).toHaveBeenCalledWith(12)
    expect(expandedId.value).toBe(12)
    expect(detailData.value).toEqual({
      id: 12,
      algorithmQuestionId: 101,
      questionTitle: '接雨水',
      code: 'print(1)',
      language: 'python',
      testResults: [
        {
          success: true,
          result: '1',
          errorMessage: null,
        },
      ],
      passCount: 3,
      totalCount: 5,
      codeEvaluation: '通过大部分测试用例',
      createTime: '2026-04-07T11:00:00',
    })
    expect(detailLoading.value).toBe(false)
  })

  it('keeps only the latest expanded submission detail when requests resolve out of order', async () => {
    problemApi.getSubmissionList.mockResolvedValue({
      records: [],
      total: 0,
      size: 20,
      current: 1,
      pages: 0,
    })

    let resolveFirst: ((value: any) => void) | undefined
    let resolveSecond: ((value: any) => void) | undefined
    problemApi.getSubmissionDetail
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve
      }))
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveSecond = resolve
      }))

    const problemId = ref<number | undefined>(101)
    const { toggleExpand, expandedId, detailData, detailLoading } = useSubmissionList(problemId)

    await Promise.resolve()
    await Promise.resolve()

    const firstRequest = toggleExpand(12)
    const secondRequest = toggleExpand(13)

    expect(expandedId.value).toBe(13)
    expect(detailLoading.value).toBe(true)

    resolveFirst?.({ id: 12, questionTitle: '旧详情' })
    await firstRequest
    await nextTick()

    expect(expandedId.value).toBe(13)
    expect(detailData.value).toBeNull()
    expect(detailLoading.value).toBe(true)

    resolveSecond?.({ id: 13, questionTitle: '新详情' })
    await secondRequest
    await nextTick()

    expect(detailData.value).toEqual({ id: 13, questionTitle: '新详情', testResults: [] })
    expect(detailLoading.value).toBe(false)
  })

  it('clears detail loading and ignores in-flight detail completion when problem changes', async () => {
    problemApi.getSubmissionList.mockResolvedValue({
      records: [],
      total: 0,
      size: 20,
      current: 1,
      pages: 0,
    })

    let resolveDetail: ((value: any) => void) | undefined
    problemApi.getSubmissionDetail.mockImplementationOnce(() => new Promise((resolve) => {
      resolveDetail = resolve
    }))

    const problemId = ref<number | undefined>(101)
    const { toggleExpand, expandedId, detailData, detailLoading } = useSubmissionList(problemId)

    await Promise.resolve()
    await Promise.resolve()

    const detailRequest = toggleExpand(12)

    expect(expandedId.value).toBe(12)
    expect(detailLoading.value).toBe(true)

    problemId.value = 202
    await nextTick()
    await Promise.resolve()

    expect(expandedId.value).toBeNull()
    expect(detailData.value).toBeNull()
    expect(detailLoading.value).toBe(false)

    resolveDetail?.({ id: 12, questionTitle: '过期详情' })
    await detailRequest
    await nextTick()

    expect(detailData.value).toBeNull()
    expect(detailLoading.value).toBe(false)
  })

  it('normalizes submission detail testResults JSON string to frontend result shape', async () => {
    problemApi.getSubmissionList.mockResolvedValue({
      records: [
        {
          id: 20,
          algorithmQuestionId: 101,
          questionTitle: '两数之和',
          passCount: 2,
          totalCount: 2,
          language: 'python',
          createTime: '2026-04-07T11:00:00',
        },
      ],
      total: 1,
      size: 20,
      current: 1,
      pages: 1,
    })
    problemApi.getSubmissionDetail.mockResolvedValue({
      id: 20,
      algorithmQuestionId: 101,
      questionTitle: '两数之和',
      code: 'print(1)',
      language: 'python',
      testResults: '{"success":true,"results":[{"passed":true,"output":"1","error":null}]}',
      passCount: 1,
      totalCount: 1,
      codeEvaluation: '全部通过',
      createTime: '2026-04-07T11:00:00',
    })

    const problemId = ref<number | undefined>(101)
    const { toggleExpand, detailData } = useSubmissionList(problemId)

    await Promise.resolve()
    await Promise.resolve()
    await toggleExpand(20)

    expect(detailData.value?.testResults).toEqual([
      {
        success: true,
        result: '1',
        errorMessage: null,
      },
    ])
  })

  it('normalizes submission detail testResults array in passed/output/error shape', async () => {
    problemApi.getSubmissionList.mockResolvedValue({
      records: [
        {
          id: 21,
          algorithmQuestionId: 101,
          questionTitle: '两数之和',
          passCount: 1,
          totalCount: 1,
          language: 'python',
          createTime: '2026-04-07T11:00:00',
        },
      ],
      total: 1,
      size: 20,
      current: 1,
      pages: 1,
    })
    problemApi.getSubmissionDetail.mockResolvedValue({
      id: 21,
      algorithmQuestionId: 101,
      questionTitle: '两数之和',
      code: 'print(1)',
      language: 'python',
      testResults: [
        {
          passed: true,
          output: '1',
          error: null,
        },
      ],
      passCount: 1,
      totalCount: 1,
      codeEvaluation: '全部通过',
      createTime: '2026-04-07T11:00:00',
    })

    const problemId = ref<number | undefined>(101)
    const { toggleExpand, detailData } = useSubmissionList(problemId)

    await Promise.resolve()
    await Promise.resolve()
    await toggleExpand(21)

    expect(detailData.value?.testResults).toEqual([
      {
        success: true,
        result: '1',
        errorMessage: null,
      },
    ])
  })
})
