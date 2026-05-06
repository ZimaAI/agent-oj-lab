import { describe, expect, it } from 'vitest'
import type { ConversationMessage } from '@/types/chat'
import { findLatestAgentMessageId, sortMessagesChronologically } from '../ChatPanel.vue'

const createMessage = (
  id: string | number,
  overrides: Partial<ConversationMessage> = {},
): ConversationMessage => ({
  id,
  conversationId: 'conversation-1',
  sender: 'USER',
  content: `message-${id}`,
  createTime: '2026-04-07T10:00:00Z',
  ...overrides,
})

describe('sortMessagesChronologically', () => {
  it('sorts out-of-order paged records by sequence number and preserves chronological prepend order', () => {
    const firstPage = [
      createMessage(3, { sequenceNo: 3, content: 'third' }),
      createMessage(2, { sequenceNo: 2, content: 'second' }),
    ]
    const olderPage = [
      createMessage(4, { sequenceNo: 4, content: 'fourth' }),
      createMessage(1, { sequenceNo: 1, content: 'first' }),
    ]

    const normalizedFirstPage = sortMessagesChronologically(firstPage)
    const combinedMessages = sortMessagesChronologically([
      ...sortMessagesChronologically(olderPage),
      ...normalizedFirstPage,
    ])

    expect(normalizedFirstPage.map((message) => message.sequenceNo)).toEqual([2, 3])
    expect(combinedMessages.map((message) => message.sequenceNo)).toEqual([1, 2, 3, 4])
  })

  it('falls back to createTime ascending when sequence number is missing', () => {
    const pageRecords = [
      createMessage(2, { createTime: '2026-04-07T10:02:00Z' }),
      createMessage(1, { createTime: '2026-04-07T10:01:00Z' }),
      createMessage(3, { createTime: '2026-04-07T10:03:00Z' }),
    ]

    const sortedMessages = sortMessagesChronologically(pageRecords)

    expect(sortedMessages.map((message) => message.id)).toEqual([1, 2, 3])
  })

  it('uses string-safe ids as a deterministic tie-breaker when createTime is identical', () => {
    const pageRecords = [
      createMessage('msg-10'),
      createMessage('msg-2'),
      createMessage('msg-1'),
    ]

    const sortedMessages = sortMessagesChronologically(pageRecords)

    expect(sortedMessages.map((message) => message.id)).toEqual(['msg-1', 'msg-10', 'msg-2'])
  })

  it('finds the latest agent message id for node-hint anchoring', () => {
    const messageList = [
      createMessage('u-1', { sender: 'USER', createTime: '2026-04-07T10:00:00Z' }),
      createMessage('a-1', { sender: 'AGENT', createTime: '2026-04-07T10:01:00Z' }),
      createMessage('u-2', { sender: 'USER', createTime: '2026-04-07T10:02:00Z' }),
      createMessage('a-2', { sender: 'AGENT', createTime: '2026-04-07T10:03:00Z' }),
    ]

    expect(findLatestAgentMessageId(messageList)).toBe('a-2')
    expect(findLatestAgentMessageId(messageList.filter((message) => message.sender === 'USER'))).toBeNull()
  })
})
