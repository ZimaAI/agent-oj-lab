import { beforeEach, describe, expect, it, vi } from 'vitest'

const { httpPost, httpGet } = vi.hoisted(() => ({
  httpPost: vi.fn(),
  httpGet: vi.fn(),
}))

vi.mock('@/utils/http', () => ({
  http: {
    post: httpPost,
    get: httpGet,
  },
}))

import { problemApi } from '../problem'

describe('problem api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('exposes the current problem and history endpoints without legacy sync submit api', () => {
    problemApi.listProblems({ pageNum: 2, pageSize: 10, type: 'SYSTEM', tagIds: [3], keyword: 'dp' })
    problemApi.getProblemById(7)
    problemApi.executeCode('print(1)', 9, 'PYTHON')
    problemApi.getSubmissionList({ algorithmQuestionId: 9, pageNum: 1, pageSize: 5 })
    problemApi.getSubmissionDetail(12)

    expect(httpPost).toHaveBeenNthCalledWith(1, '/api/algorithm-question/page', {
      pageNum: 2,
      pageSize: 10,
      difficulty: undefined,
      type: 'SYSTEM',
      keyword: 'dp',
      tagIds: [3],
    })
    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/algorithm-question/7')
    expect(httpPost).toHaveBeenNthCalledWith(2, '/api/code/execute', {
      code: 'print(1)',
      algorithmQuestionId: 9,
      language: 'PYTHON',
    })
    expect(httpPost).toHaveBeenNthCalledWith(3, '/api/code/submissions', {
      algorithmQuestionId: 9,
      pageNum: 1,
      pageSize: 5,
    })
    expect(httpGet).toHaveBeenNthCalledWith(2, '/api/code/submissions/12')
    expect(problemApi).not.toHaveProperty('submitCode')
  })

  it('returns backend pagination fields for problem list page calls', async () => {
    httpPost.mockResolvedValueOnce({
      records: [],
      total: 41,
      size: 10,
      current: 2,
      pages: 5,
    })

    const result = await problemApi.listProblems({ pageNum: 2, pageSize: 10, keyword: 'dp' })

    expect(result.total).toBe(41)
    expect(result.pages).toBe(5)
    expect(result.current).toBe(2)
  })
})
