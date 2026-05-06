import { beforeEach, describe, expect, it, vi } from 'vitest'

const { httpPost, httpGet, httpPut, httpRequest } = vi.hoisted(() => ({
  httpPost: vi.fn(),
  httpGet: vi.fn(),
  httpPut: vi.fn(),
  httpRequest: vi.fn(),
}))

vi.mock('@/utils/http', () => ({
  http: {
    post: httpPost,
    get: httpGet,
    put: httpPut,
    request: httpRequest,
  },
}))

import { adminQuestionApi } from '../adminQuestion'

describe('admin question api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('normalizes pagination payload and sends both pageNum/pageSize and current/size', () => {
    adminQuestionApi.pageQuestions({
      current: 2,
      size: 15,
      keyword: 'dp',
      type: 'SYSTEM',
    })

    expect(httpPost).toHaveBeenCalledWith('/api/algorithm-question/page', {
      pageNum: 2,
      pageSize: 15,
      current: 2,
      size: 15,
      keyword: 'dp',
      difficulty: undefined,
      type: 'SYSTEM',
    })
  })

  it('prefers explicit pageNum/pageSize when both styles are provided', () => {
    adminQuestionApi.pageQuestions({
      pageNum: 3,
      pageSize: 9,
      current: 7,
      size: 11,
      difficulty: 'HARD',
    })

    expect(httpPost).toHaveBeenCalledWith('/api/algorithm-question/page', {
      pageNum: 3,
      pageSize: 9,
      current: 3,
      size: 9,
      keyword: undefined,
      difficulty: 'HARD',
      type: undefined,
    })
  })

  it('supports create question and question document APIs', () => {
    adminQuestionApi.listTags()
    adminQuestionApi.createQuestion({
      title: 'Two Sum',
      description: 'desc',
      difficulty: 'MEDIUM',
      sharedFunctionName: 'twoSum',
      sharedCodeSkeleton: 'class Solution {}',
      sharedTestCases: '[{"input":{"nums":[2,7,11,15],"target":9},"expectedOutput":[0,1]}]',
      tagIds: [1],
      tags: ['Array', 'Hash'],
      codeTemplates: [
        { language: 'JAVA', functionName: 'twoSum', codeSkeleton: 'class Solution {}', referenceAnswer: 'return null;' },
        { language: 'PYTHON', functionName: 'two_sum', codeSkeleton: 'def two_sum(): pass', referenceAnswer: 'return []' },
        { language: 'JAVASCRIPT', functionName: 'twoSum', codeSkeleton: 'function twoSum() {}', referenceAnswer: 'return []' },
      ],
    })

    const file = new Blob(['# two-sum'], { type: 'text/markdown' }) as File
    adminQuestionApi.uploadQuestionDocument(1001, file)
    adminQuestionApi.listQuestionDocuments(1001)
    adminQuestionApi.deleteQuestionDocument(1001, 2001)
    adminQuestionApi.pageQuestionDocumentSegments(1001, 2001, { current: 2, size: 15 })
    adminQuestionApi.listQuestionDocumentChildSegments(1001, 2001, 3001)

    const [, uploadPayload] = httpPost.mock.calls[1] as [string, FormData]

    expect(httpPost).toHaveBeenNthCalledWith(1, '/api/admin/questions', expect.any(Object))
    expect(httpPost).toHaveBeenNthCalledWith(2, '/api/admin/questions/1001/documents/upload', expect.any(FormData))
    const uploadedFile = uploadPayload.get('file')
    expect(uploadedFile).toBeInstanceOf(File)
    expect((uploadedFile as File).type).toBe('text/markdown')
    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/tag/list')
    expect(httpGet).toHaveBeenNthCalledWith(2, '/api/admin/questions/1001/documents')
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001', {
      method: 'DELETE',
    })
    expect(httpGet).toHaveBeenNthCalledWith(3, '/api/admin/questions/1001/documents/2001/segments', {
      current: 2,
      size: 15,
    })
    expect(httpGet).toHaveBeenNthCalledWith(4, '/api/admin/questions/1001/documents/2001/segments/3001/children')
  })

  it('supports hitk question generation, update, and hitk test task APIs', () => {
    adminQuestionApi.generateQuestionDocumentSegmentHitkQuestion(1001, 2001, {
      segmentIds: [3001, 3002],
    })
    adminQuestionApi.updateQuestionDocumentSegmentHitkQuestion(1001, 2001, {
      updates: [
        { segmentId: 3001, hitkQuestion: '什么是动态规划状态定义？' },
        { segmentId: 3002, hitkQuestion: '如何判断转移方程是否正确？' },
      ],
    })
    adminQuestionApi.createQuestionDocumentSegmentHitkTestTask(1001, 2001, {
      segmentIds: [3001, 3002],
    })
    adminQuestionApi.listQuestionDocumentSegmentHitkTestTasks(1001, 2001)
    adminQuestionApi.getQuestionDocumentSegmentHitkTestTaskDetail(1001, 2001, 5001)

    expect(httpPost).toHaveBeenNthCalledWith(
      1,
      '/api/admin/questions/1001/documents/2001/segments/hitk-question/generate',
      {
        segmentIds: [3001, 3002],
      },
    )
    expect(httpPut).toHaveBeenNthCalledWith(
      1,
      '/api/admin/questions/1001/documents/2001/segments/hitk-question',
      {
        updates: [
          { segmentId: 3001, hitkQuestion: '什么是动态规划状态定义？' },
          { segmentId: 3002, hitkQuestion: '如何判断转移方程是否正确？' },
        ],
      },
    )
    expect(httpPost).toHaveBeenNthCalledWith(
      2,
      '/api/admin/questions/1001/documents/2001/segments/hitk-tests',
      {
        segmentIds: [3001, 3002],
      },
    )
    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001/segments/hitk-tests')
    expect(httpGet).toHaveBeenNthCalledWith(2, '/api/admin/questions/1001/documents/2001/segments/hitk-tests/5001')
  })

  it('supports ragas generation, query, update, delete, answer generation, and evaluate APIs', () => {
    adminQuestionApi.generateQuestionDocumentSegmentRagas(1001, 2001, {
      segmentIds: [3001, 3002],
    })
    adminQuestionApi.listQuestionDocumentSegmentRagas(1001, 2001, {
      segmentIds: [3001, 3002],
    })
    adminQuestionApi.updateQuestionDocumentSegmentRagas(1001, 2001, {
      updates: [
        { segmentId: 3001, question: '动态规划的状态是什么？', standardAnswer: '状态通常由下标与附加条件构成。' },
        { ragasId: 9002, segmentId: 3002, question: '如何设计转移？', standardAnswer: '先定义子问题，再枚举决策。' },
      ],
    })
    adminQuestionApi.deleteQuestionDocumentSegmentRagas(1001, 2001, {
      segmentIds: [3001],
      ragasIds: [9002],
    })
    adminQuestionApi.generateQuestionDocumentSegmentRagasAnswers(1001, 2001, {
      segmentIds: [3001, 3002],
    })
    adminQuestionApi.evaluateQuestionDocumentSegmentRagas(1001, 2001, {
      segmentIds: [3001, 3002],
    })

    expect(httpPost).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001/segments/ragas/generate', {
      segmentIds: [3001, 3002],
    })
    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001/segments/ragas', {
      segmentIds: '3001,3002',
    })
    expect(httpPut).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001/segments/ragas', {
      updates: [
        { segmentId: 3001, question: '动态规划的状态是什么？', standardAnswer: '状态通常由下标与附加条件构成。' },
        { ragasId: 9002, segmentId: 3002, question: '如何设计转移？', standardAnswer: '先定义子问题，再枚举决策。' },
      ],
    })
    expect(httpRequest).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001/segments/ragas', {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        segmentIds: [3001],
        ragasIds: [9002],
      }),
    })
    expect(httpPost).toHaveBeenNthCalledWith(
      2,
      '/api/admin/questions/1001/documents/2001/segments/ragas/answers/generate',
      {
        segmentIds: [3001, 3002],
      },
    )
    expect(httpPost).toHaveBeenNthCalledWith(3, '/api/admin/questions/1001/documents/2001/segments/ragas/evaluate', {
      segmentIds: [3001, 3002],
    })
  })

  it('supports ragas task create, list, page, and detail APIs', () => {
    adminQuestionApi.createQuestionDocumentSegmentRagasTask(1001, 2001, {
      segmentIds: [3001, 3002],
    })
    adminQuestionApi.listQuestionDocumentSegmentRagasTasks(1001, 2001)
    adminQuestionApi.pageRagasTasks({
      current: 2,
      size: 10,
      status: 'COMPLETED',
      startTime: '2026-05-01T00:00:00',
      endTime: '2026-05-06T00:00:00',
    })
    adminQuestionApi.getRagasTaskDetail(7001)

    expect(httpPost).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001/segments/ragas/tasks', {
      segmentIds: [3001, 3002],
    })
    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/admin/questions/1001/documents/2001/segments/ragas/tasks')
    expect(httpGet).toHaveBeenNthCalledWith(2, '/api/admin/questions/ragas-tasks', {
      current: 2,
      size: 10,
      status: 'COMPLETED',
      startTime: '2026-05-01T00:00:00',
      endTime: '2026-05-06T00:00:00',
    })
    expect(httpGet).toHaveBeenNthCalledWith(3, '/api/admin/questions/ragas-tasks/7001')
  })

  it('supports global knowledge segment and hitk task management APIs', () => {
    adminQuestionApi.pageKnowledgeSegments({
      current: 2,
      size: 15,
      keyword: '命中',
    })
    adminQuestionApi.pageHitkTasks({
      current: 3,
      size: 20,
      status: 'COMPLETED',
      startTime: '2026-04-20T00:00:00',
      endTime: '2026-04-27T00:00:00',
    })
    adminQuestionApi.calculateHitkTaskStatistics({
      taskIds: [9001, 9002, 9003],
    })
    adminQuestionApi.batchDeleteHitkTasks({
      taskIds: [9001, 9002],
    })
    adminQuestionApi.updateHitkTaskRemark(9001, {
      remark: '夜间任务',
    })

    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/admin/questions/knowledge-segments', {
      current: 2,
      size: 15,
      keyword: '命中',
    })
    expect(httpGet).toHaveBeenNthCalledWith(2, '/api/admin/questions/hitk-tasks', {
      current: 3,
      size: 20,
      status: 'COMPLETED',
      startTime: '2026-04-20T00:00:00',
      endTime: '2026-04-27T00:00:00',
    })
    expect(httpPost).toHaveBeenNthCalledWith(1, '/api/admin/questions/hitk-tasks/statistics', {
      taskIds: [9001, 9002, 9003],
    })
    expect(httpPost).toHaveBeenNthCalledWith(2, '/api/admin/questions/hitk-tasks/batch-delete', {
      taskIds: [9001, 9002],
    })
    expect(httpPut).toHaveBeenNthCalledWith(1, '/api/admin/questions/hitk-tasks/9001/remark', {
      remark: '夜间任务',
    })
  })
})
