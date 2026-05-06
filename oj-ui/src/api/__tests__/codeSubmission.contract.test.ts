import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/apiBaseUrl', () => ({
  getApiBaseUrl: () => 'http://localhost:3001',
}))

const { parseStreamResponse } = vi.hoisted(() => ({
  parseStreamResponse: vi.fn(),
}))

vi.mock('@/utils/streamParser', () => ({
  parseStreamResponse,
}))

import { submitCodeStream } from '../codeSubmission'

describe('code submission api contract', () => {
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

  it('streams code evaluation through the verified workflow endpoint with string conversation ids', async () => {
    const response = new Response('')
    globalThis.fetch = vi.fn().mockResolvedValue(response) as any
    parseStreamResponse.mockResolvedValue(undefined)

    localStorage.setItem('access_token', 'test-token')

    await submitCodeStream(
      {
        code: 'print(1)',
        algorithmQuestionId: 101,
        conversationId: 'conversation-101',
        language: 'python',
      },
      {
        onAssistantToken: vi.fn(),
        onOverlayText: vi.fn(),
        onStructuredResult: vi.fn(),
        onIntent: vi.fn(),
        onComplete: vi.fn(),
        onError: vi.fn(),
      },
    )

    expect(globalThis.fetch).toHaveBeenCalledWith('http://localhost:3001/api/workflow/code-evaluation/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer test-token',
      },
      body: JSON.stringify({
        code: 'print(1)',
        algorithmQuestionId: 101,
        conversationId: 'conversation-101',
        language: 'python',
      }),
      signal: undefined,
    })
    expect(parseStreamResponse).toHaveBeenCalledWith(response, expect.any(Object))
  })

  it('silently ignores aborted code submission streams', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new DOMException('aborted', 'AbortError')) as any
    const onError = vi.fn()

    await submitCodeStream(
      {
        code: 'print(1)',
        algorithmQuestionId: 101,
        conversationId: 'conversation-101',
        language: 'python',
      },
      {
        onAssistantToken: vi.fn(),
        onOverlayText: vi.fn(),
        onStructuredResult: vi.fn(),
        onIntent: vi.fn(),
        onComplete: vi.fn(),
        onError,
      },
    )

    expect(onError).not.toHaveBeenCalled()
  })

  it('handles GraphNodeResponse-style code evaluation stream events from backend', async () => {
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
        conversationId: 'conversation-101',
        type: 'overlayText',
        data: {
          text: '开始评审',
          nodeName: 'IntentRecognitionNode',
          raw: {
            conservationId: 'conversation-101',
            nodeName: 'IntentRecognitionNode',
            textType: 'TEXT',
            text: '开始评审',
            error: false,
            complete: false,
          },
        },
        timestamp: 1,
      } as any)
      callbacks.onChunk({
        conversationId: 'conversation-101',
        type: 'assistantToken',
        data: '评审完成',
        timestamp: 2,
      } as any)
      callbacks.onChunk({
        conversationId: 'conversation-101',
        type: 'complete',
        data: null,
        timestamp: 3,
      } as any)
    })

    await submitCodeStream(
      {
        code: 'print(1)',
        algorithmQuestionId: 101,
        conversationId: 'conversation-101',
        language: 'python',
      },
      {
        onAssistantToken,
        onOverlayText,
        onStructuredResult,
        onIntent,
        onComplete,
        onError,
      },
    )

    expect(onOverlayText).toHaveBeenCalled()
    expect(onAssistantToken).toHaveBeenCalledWith('评审完成')
    expect(onStructuredResult).not.toHaveBeenCalled()
    expect(onIntent).not.toHaveBeenCalled()
    expect(onComplete).toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })
})
