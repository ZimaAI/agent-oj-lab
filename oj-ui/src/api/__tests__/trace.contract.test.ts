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

import { getTraceDetail, getTraceItems, listTraceItems, pageTraces } from '../trace'
import { listByTraceId, pageSpanRecords } from '../spanRecord'

describe('trace api contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('pages traces through the backend trace page endpoint with the new query fields', async () => {
    httpPost.mockResolvedValue({
      records: [
        {
          id: 1,
          traceId: 'trace-1',
          conversationId: 'conversation-1',
          userId: 7,
          requestMessage: '给我一道动态规划题',
          currentAlgorithmQuestionId: 101,
          status: 'SUCCESS',
          errorMessage: null,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
      ],
      total: 1,
      size: 10,
      current: 1,
      pages: 1,
    })

    const result = await pageTraces({
      pageNum: 1,
      pageSize: 10,
      traceId: 'trace-1',
      requestMessage: '动态规划',
      status: 'SUCCESS',
    })

    expect(httpPost).toHaveBeenCalledWith('/api/trace/page', {
      pageNum: 1,
      pageSize: 10,
      traceId: 'trace-1',
      requestMessage: '动态规划',
      status: 'SUCCESS',
    })
    expect(result.total).toBe(1)
    expect(result.pages).toBe(1)
    expect(result.records[0]?.requestMessage).toBe('给我一道动态规划题')
  })

  it('pages legacy span records with filtered trace items', async () => {
    httpGet.mockResolvedValueOnce([
      {
        id: 11,
        traceId: 'trace-1',
        nodeName: 'intentRecognitionNode',
        itemType: 'LLM',
        itemKey: 'messages',
        roundNo: 1,
        toolName: null,
        inputPayload: '[{"role":"user","content":"给我一道动态规划题"}]',
        outputPayload: '{"intent":"NEW_QUESTION"}',
        inputSummary: '1 条消息',
        outputSummary: '识别为新题请求',
        promptTokens: 12,
        completionTokens: 8,
        totalTokens: 20,
        status: 'SUCCESS',
        errorMessage: null,
        startTimestamp: 1712484000000,
        endTimestamp: 1712484001000,
        createTime: '2026-04-07 10:00:00',
        updateTime: '2026-04-07 10:00:01',
      },
      {
        id: 12,
        traceId: 'trace-1',
        nodeName: 'codeQuestionNode',
        itemType: 'LLM',
        itemKey: 'draft',
        roundNo: 1,
        toolName: null,
        inputPayload: '{}',
        outputPayload: '{}',
        inputSummary: '生成新题',
        outputSummary: '输出题面',
        promptTokens: 24,
        completionTokens: 32,
        totalTokens: 56,
        status: 'SUCCESS',
        errorMessage: null,
        startTimestamp: 1712484002000,
        endTimestamp: 1712484003000,
        createTime: '2026-04-07 10:00:02',
        updateTime: '2026-04-07 10:00:03',
      },
      {
        id: 13,
        traceId: 'trace-1',
        nodeName: 'ojAssistantNode',
        itemType: 'LLM',
        itemKey: 'reply',
        roundNo: 1,
        toolName: null,
        inputPayload: '{}',
        outputPayload: '{}',
        inputSummary: '组装回复',
        outputSummary: '回复用户',
        promptTokens: 16,
        completionTokens: 18,
        totalTokens: 34,
        status: 'FAILED',
        errorMessage: '超时',
        startTimestamp: 1712484004000,
        endTimestamp: 1712484005000,
        createTime: '2026-04-07 10:00:04',
        updateTime: '2026-04-07 10:00:05',
      },
    ])

    const result = await pageSpanRecords({
      pageNum: 2,
      pageSize: 1,
      traceId: 'trace-1',
      itemType: 'LLM',
      status: 'SUCCESS',
    })

    expect(httpGet).toHaveBeenCalledWith('/api/trace/trace-1/items')
    expect(result).toEqual({
      records: [
        expect.objectContaining({
          id: 12,
          nodeName: 'codeQuestionNode',
          status: 'SUCCESS',
        }),
      ],
      total: 2,
      size: 1,
      current: 2,
      pages: 2,
    })
  })

  it('loads trace detail and items from the verified trace resource endpoints', async () => {
    httpGet
      .mockResolvedValueOnce({
        id: 1,
        traceId: 'trace-1',
        conversationId: 'conversation-1',
        userId: 7,
        requestMessage: '给我一道动态规划题',
        currentAlgorithmQuestionId: 101,
        currentAlgorithmQuestionSnapshot: '{"title":"动态规划入门"}',
        status: 'SUCCESS',
        errorMessage: null,
        startTimestamp: 1712484000000,
        endTimestamp: 1712484001000,
        createTime: '2026-04-07 10:00:00',
        updateTime: '2026-04-07 10:00:01',
      })
      .mockResolvedValueOnce([
        {
          id: 11,
          traceId: 'trace-1',
          nodeName: 'intentRecognitionNode',
          itemType: 'LLM',
          itemKey: 'messages',
          roundNo: 1,
          toolName: null,
          inputPayload: '[{"role":"user","content":"给我一道动态规划题"}]',
          outputPayload: '{"intent":"NEW_QUESTION"}',
          inputSummary: '1 条消息',
          outputSummary: '识别为新题请求',
          promptTokens: 12,
          completionTokens: 8,
          totalTokens: 20,
          status: 'SUCCESS',
          errorMessage: null,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
      ])
      .mockResolvedValueOnce([
        {
          id: 11,
          traceId: 'trace-1',
          nodeName: 'intentRecognitionNode',
          itemType: 'LLM',
          itemKey: 'messages',
          roundNo: 1,
          toolName: null,
          inputPayload: '[{"role":"user","content":"给我一道动态规划题"}]',
          outputPayload: '{"intent":"NEW_QUESTION"}',
          inputSummary: '1 条消息',
          outputSummary: '识别为新题请求',
          promptTokens: 12,
          completionTokens: 8,
          totalTokens: 20,
          status: 'SUCCESS',
          errorMessage: null,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
      ])
      .mockResolvedValueOnce([
        {
          id: 11,
          traceId: 'trace-1',
          nodeName: 'intentRecognitionNode',
          itemType: 'LLM',
          itemKey: 'messages',
          roundNo: 1,
          toolName: null,
          inputPayload: '[{"role":"user","content":"给我一道动态规划题"}]',
          outputPayload: '{"intent":"NEW_QUESTION"}',
          inputSummary: '1 条消息',
          outputSummary: '识别为新题请求',
          promptTokens: 12,
          completionTokens: 8,
          totalTokens: 20,
          status: 'SUCCESS',
          errorMessage: null,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
      ])

    const detail = await getTraceDetail('trace-1')
    const items = await listTraceItems('trace-1')
    const legacyItems = await listByTraceId('trace-1')
    const aliasItems = await getTraceItems('trace-1')

    expect(httpGet).toHaveBeenNthCalledWith(1, '/api/trace/trace-1')
    expect(httpGet).toHaveBeenNthCalledWith(2, '/api/trace/trace-1/items')
    expect(httpGet).toHaveBeenNthCalledWith(3, '/api/trace/trace-1/items')
    expect(httpGet).toHaveBeenNthCalledWith(4, '/api/trace/trace-1/items')
    expect(detail.currentAlgorithmQuestionSnapshot).toContain('动态规划入门')
    expect(items[0]?.itemType).toBe('LLM')
    expect(legacyItems[0]?.nodeName).toBe('intentRecognitionNode')
    expect(aliasItems[0]?.itemKey).toBe('messages')
  })

  it('encodes traceId path segments when requesting trace detail endpoints', async () => {
    const traceId = 'trace/a?b=1#片段'
    const encodedTraceId = encodeURIComponent(traceId)

    httpGet.mockResolvedValueOnce({
      id: 1,
      traceId,
      conversationId: 'conversation-1',
      userId: 7,
      requestMessage: '给我一道动态规划题',
      currentAlgorithmQuestionId: 101,
      currentAlgorithmQuestionSnapshot: '{"title":"动态规划入门"}',
      status: 'SUCCESS',
      errorMessage: null,
      createTime: '2026-04-07 10:00:00',
      updateTime: '2026-04-07 10:00:01',
    })
    httpGet.mockResolvedValueOnce([])

    await getTraceDetail(traceId)
    await listTraceItems(traceId)

    expect(httpGet).toHaveBeenNthCalledWith(1, `/api/trace/${encodedTraceId}`)
    expect(httpGet).toHaveBeenNthCalledWith(2, `/api/trace/${encodedTraceId}/items`)
  })
})
