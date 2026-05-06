import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock('@/utils/http', () => ({
  http,
}))

vi.mock('@/utils/apiBaseUrl', () => ({
  getApiBaseUrl: () => 'http://localhost:3001',
}))

const { parseStreamResponse } = vi.hoisted(() => ({
  parseStreamResponse: vi.fn(),
}))

vi.mock('@/utils/streamParser', () => ({
  parseStreamResponse,
}))

import {
  createConversation,
  deleteConversation,
  getConversationList,
  getConversationSummaryById,
  getMessageList,
  sendMessageStream,
} from '../conversation'

describe('conversation api contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()

    const storage = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => {
        storage.set(key, value)
      }),
      removeItem: vi.fn((key: string) => {
        storage.delete(key)
      }),
      clear: vi.fn(() => {
        storage.clear()
      }),
    })
  })

  it('creates conversations through the backend conversations resource and returns the backend vo shape', async () => {
    http.post.mockResolvedValue({
      conversationId: '101',
      title: '动态规划入门',
      lastMessageTime: '2026-04-07T10:00:00',
      sessionStatus: 'ACTIVE' as const,
      currentQuestionId: 88,
      createTime: '2026-04-07T09:59:00',
    })

    const result = await createConversation({ currentQuestionId: 88 })

    expect(http.post).toHaveBeenCalledWith('/api/conversations', { currentQuestionId: 88 })
    expect(result.conversationId).toBe('101')
  })

  it('normalizes legacy questionId to currentQuestionId when creating a conversation', async () => {
    http.post.mockResolvedValue({
      conversationId: '102',
      title: 'new conversation',
      lastMessageTime: '2026-04-07T10:01:00',
      sessionStatus: 'ACTIVE' as const,
      currentQuestionId: 99,
      createTime: '2026-04-07T10:01:00',
    })

    await createConversation({ questionId: 99 } as any)

    expect(http.post).toHaveBeenCalledWith('/api/conversations', { currentQuestionId: 99 })
  })

  it('loads conversation pages from the backend conversations collection endpoint', async () => {
    http.get.mockResolvedValue({
      records: [
        {
          conversationId: '101',
          title: '动态规划入门',
          lastMessageTime: '2026-04-07T10:00:00',
          sessionStatus: 'ACTIVE' as const,
          currentQuestionId: 88,
          latestMessagePreview: '请给我一道动态规划题',
          createTime: '2026-04-07T09:59:00',
        },
      ],
      total: 1,
      size: 10,
      current: 1,
      pages: 1,
    })

    const result = await getConversationList({ current: 1, pageSize: 10 })

    expect(http.get).toHaveBeenCalledWith('/api/conversations', {
      params: {
        current: 1,
        pageSize: 10,
      },
    })
    expect(result.records[0]?.latestMessagePreview).toBe('请给我一道动态规划题')
  })

  it('finds a conversation summary by id using only the paged conversations collection endpoint', async () => {
    http.get
      .mockResolvedValueOnce({
        records: [
          {
            conversationId: '100',
            title: '二分查找入门',
            lastMessageTime: '2026-04-07T09:00:00',
            sessionStatus: 'ACTIVE' as const,
            currentQuestionId: 66,
            latestMessagePreview: '请给我一道二分题',
            createTime: '2026-04-07T08:59:00',
          },
        ],
        total: 2,
        size: 1,
        current: 1,
        pages: 2,
      })
      .mockResolvedValueOnce({
        records: [
          {
            conversationId: '101',
            title: '动态规划入门',
            lastMessageTime: '2026-04-07T10:00:00',
            sessionStatus: 'ACTIVE' as const,
            currentQuestionId: 88,
            latestMessagePreview: '请给我一道动态规划题',
            createTime: '2026-04-07T09:59:00',
          },
        ],
        total: 2,
        size: 1,
        current: 2,
        pages: 2,
      })

    const conversation = await getConversationSummaryById('101', { pageSize: 1 })

    expect(http.get).toHaveBeenNthCalledWith(1, '/api/conversations', {
      params: {
        current: 1,
        pageSize: 1,
      },
    })
    expect(http.get).toHaveBeenNthCalledWith(2, '/api/conversations', {
      params: {
        current: 2,
        pageSize: 1,
      },
    })
    expect(conversation?.currentQuestionId).toBe(88)
  })

  it('loads conversation messages through the backend conversations resource path', async () => {
    http.get.mockResolvedValue({
      records: [
        {
          sender: 'USER',
          content: '请给我一道动态规划题',
          messageType: 'PLAIN',
          sequenceNo: 1,
          createTime: '2026-04-07T10:00:00',
          resultType: null,
          resultSummary: null,
        },
      ],
      total: 1,
      size: 20,
      current: 1,
      pages: 1,
    })

    const messages = await getMessageList('101', { current: 1, pageSize: 20 })

    expect(http.get).toHaveBeenCalledWith('/api/conversations/101/messages', {
      params: {
        current: 1,
        pageSize: 20,
      },
    })
    expect(messages.records[0]?.sequenceNo).toBe(1)
  })

  it('streams conversation messages through the verified workflow stream endpoint and deletes conversations through the backend resource path', async () => {
    const response = new Response('')
    globalThis.fetch = vi.fn().mockResolvedValue(response) as any
    parseStreamResponse.mockResolvedValue(undefined)
    http.delete.mockResolvedValue(undefined)

    localStorage.setItem('access_token', 'test-token')

    await sendMessageStream(
      '101',
      '继续下一题',
      {
        onAssistantToken: vi.fn(),
        onOverlayText: vi.fn(),
        onStructuredResult: vi.fn(),
        onIntent: vi.fn(),
        onComplete: vi.fn(),
        onError: vi.fn(),
      },
    )
    await deleteConversation('101')

    expect(globalThis.fetch).toHaveBeenCalledWith('http://localhost:3001/api/workflow/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer test-token',
      },
      body: JSON.stringify({
        conversationId: '101',
        message: '继续下一题',
      }),
      signal: undefined,
    })
    expect(parseStreamResponse).toHaveBeenCalledWith(response, expect.any(Object))
    expect(http.delete).toHaveBeenCalledWith('/api/conversations/101')
  })

  it('handles GraphNodeResponse-style workflow stream events from backend', async () => {
    const response = new Response('')
    globalThis.fetch = vi.fn().mockResolvedValue(response) as any
    const onAssistantToken = vi.fn()
    const onOverlayText = vi.fn()
    const onStructuredResult = vi.fn()
    const onIntent = vi.fn()
    const onComplete = vi.fn()
    const onError = vi.fn()

    parseStreamResponse.mockImplementation(async (_response, callbacks) => {
      callbacks.onChunk({
        conversationId: '101',
        type: 'overlayText',
        data: {
          text: '开始分析需求',
          nodeName: 'IntentRecognitionNode',
          raw: {
            conservationId: '101',
            nodeName: 'IntentRecognitionNode',
            textType: 'TEXT',
            text: '开始分析需求',
            error: false,
            complete: false,
          },
        },
        timestamp: 1,
      } as any)
      callbacks.onChunk({
        conversationId: '101',
        type: 'assistantToken',
        data: '题目生成完成',
        timestamp: 2,
      } as any)
      callbacks.onChunk({
        conversationId: '101',
        type: 'complete',
        data: null,
        timestamp: 3,
      } as any)
    })

    localStorage.setItem('access_token', 'test-token')

    await sendMessageStream('101', '继续下一题', {
      onAssistantToken,
      onOverlayText,
      onStructuredResult,
      onIntent,
      onComplete,
      onError,
    })

    expect(onOverlayText).toHaveBeenCalledWith({
      text: '开始分析需求',
      nodeName: 'IntentRecognitionNode',
      raw: {
        conservationId: '101',
        nodeName: 'IntentRecognitionNode',
        textType: 'TEXT',
        text: '开始分析需求',
        error: false,
        complete: false,
      },
    })
    expect(onAssistantToken).toHaveBeenCalledWith('题目生成完成')
    expect(onStructuredResult).not.toHaveBeenCalled()
    expect(onIntent).not.toHaveBeenCalled()
    expect(onComplete).toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })
})
