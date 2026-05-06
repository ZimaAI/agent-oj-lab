import { describe, expect, it } from 'vitest'

import { applyConversationSummaryUpdate } from '../conversationSummarySync'
import type { ConversationListItemVO } from '@/types/chat'

const buildConversation = (overrides: Partial<ConversationListItemVO> = {}): ConversationListItemVO => ({
  conversationId: '1',
  title: '新会话',
  lastMessageTime: '2026-03-21T10:00:00',
  sessionStatus: 'ACTIVE',
  currentQuestionId: null,
  latestMessagePreview: null,
  createTime: '2026-03-21T10:00:00',
  ...overrides,
})

describe('conversationSummarySync', () => {
  it('updates the matching conversation title when a new code question arrives', () => {
    const conversations = [
      buildConversation({ conversationId: '1', title: '新会话' }),
      buildConversation({ conversationId: '2', title: 'Existing Title' }),
    ]

    const updated = applyConversationSummaryUpdate(conversations, {
      id: '1',
      title: 'Two Pointers',
    })

    expect(updated).toEqual([
      buildConversation({ conversationId: '1', title: 'Two Pointers' }),
      buildConversation({ conversationId: '2', title: 'Existing Title' }),
    ])
  })

  it('keeps the list unchanged when the conversation is not present', () => {
    const conversations = [
      buildConversation({ conversationId: '2', title: 'Existing Title' }),
    ]

    const updated = applyConversationSummaryUpdate(conversations, {
      id: '1',
      title: 'Two Pointers',
    })

    expect(updated).toEqual(conversations)
  })
})
