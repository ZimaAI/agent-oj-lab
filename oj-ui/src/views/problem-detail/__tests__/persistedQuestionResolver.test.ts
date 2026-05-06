import { describe, expect, it, vi } from 'vitest'

import { resolvePersistedQuestionId } from '../persistedQuestionResolver'

describe('persistedQuestionResolver', () => {
  it('returns the streamed question id immediately when it is already persisted', async () => {
    const loadConversationSummary = vi.fn()

    const result = await resolvePersistedQuestionId({
      streamedQuestionId: 42,
      conversationId: '1',
      loadConversationSummary,
    })

    expect(result).toBe(42)
    expect(loadConversationSummary).not.toHaveBeenCalled()
  })

  it('waits for conversation currentQuestionId to catch up in strict sync mode', async () => {
    const waits: number[] = []
    const loadConversationSummary = vi
      .fn()
      .mockResolvedValueOnce({ currentQuestionId: 41 })
      .mockResolvedValueOnce({ currentQuestionId: 42 })

    const result = await resolvePersistedQuestionId({
      streamedQuestionId: 42,
      currentQuestionId: 42,
      conversationId: 'conversation-sync-42',
      loadConversationSummary,
      maxAttempts: 3,
      delayMs: 20,
      wait: async (ms) => {
        waits.push(ms)
      },
      requireConversationSync: true,
    })

    expect(result).toBe(42)
    expect(loadConversationSummary).toHaveBeenCalledTimes(2)
    expect(waits).toEqual([20])
  })

  it('resolves a persisted question through the history-mode conversation string id', async () => {
    const loadConversationSummary = vi.fn().mockResolvedValue({ currentQuestionId: 66 })

    const result = await resolvePersistedQuestionId({
      conversationId: 'conversation-history-7',
      loadConversationSummary,
    })

    expect(result).toBe(66)
    expect(loadConversationSummary).toHaveBeenCalledWith('conversation-history-7')
  })

  it('polls the verified conversation summary lookup until currentQuestionId becomes available', async () => {
    const waits: number[] = []
    const loadConversationSummary = vi
      .fn()
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce({ currentQuestionId: 88 })

    const result = await resolvePersistedQuestionId({
      conversationId: '7',
      loadConversationSummary,
      maxAttempts: 3,
      delayMs: 50,
      wait: async (ms) => {
        waits.push(ms)
      },
    })

    expect(result).toBe(88)
    expect(loadConversationSummary).toHaveBeenCalledTimes(2)
    expect(waits).toEqual([50])
  })
})
