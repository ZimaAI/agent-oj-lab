import { describe, expect, it } from 'vitest'

import {
  buildDeleteConversationConfirmText,
  buildPendingDeleteConversationDialogState,
  createInitialDeleteConversationDialogState,
  removeConversationFromList,
  resolveDeleteConversationTitle,
  resolveNextConversationIdAfterDelete,
} from '../HistorySidebar.vue'
import type { ConversationListItemVO } from '@/types/chat'

const createConversation = (
  conversationId: string,
  title: string,
): ConversationListItemVO => ({
  conversationId,
  title,
  lastMessageTime: null,
  sessionStatus: 'ACTIVE',
  currentQuestionId: null,
  latestMessagePreview: null,
  createTime: '2026-04-15T00:00:00',
})

describe('HistorySidebar delete helpers', () => {
  it('builds the delete confirmation text with title', () => {
    expect(buildDeleteConversationConfirmText('动态规划入门')).toBe('确认删除会话“动态规划入门”吗？')
  })

  it('resolves title by conversation id and falls back to id when missing', () => {
    const conversationList = [
      createConversation('c-1', '会话一'),
      createConversation('c-2', '会话二'),
    ]

    expect(resolveDeleteConversationTitle(conversationList, 'c-2')).toBe('会话二')
    expect(resolveDeleteConversationTitle(conversationList, 'c-9')).toBe('c-9')
  })

  it('removes target conversation from list', () => {
    const conversationList = [
      createConversation('c-1', '会话一'),
      createConversation('c-2', '会话二'),
      createConversation('c-3', '会话三'),
    ]

    const nextConversationList = removeConversationFromList(conversationList, 'c-2')

    expect(nextConversationList.map((conversation) => conversation.conversationId)).toEqual(['c-1', 'c-3'])
  })

  it('returns first remaining conversation when deleting active conversation', () => {
    const remainingConversationList = [
      createConversation('c-1', '会话一'),
      createConversation('c-3', '会话三'),
    ]

    expect(resolveNextConversationIdAfterDelete(remainingConversationList, 'c-2', 'c-2')).toBe('c-1')
  })

  it('returns null when deleting a non-active conversation', () => {
    const remainingConversationList = [
      createConversation('c-1', '会话一'),
      createConversation('c-3', '会话三'),
    ]

    expect(resolveNextConversationIdAfterDelete(remainingConversationList, 'c-2', 'c-1')).toBeNull()
  })

  it('returns null when no remaining conversation exists after deleting active one', () => {
    expect(resolveNextConversationIdAfterDelete([], 'c-1', 'c-1')).toBeNull()
  })

  it('builds pending delete dialog state with resolved title', () => {
    const conversationList = [
      createConversation('c-1', '会话一'),
      createConversation('c-2', '会话二'),
    ]

    expect(buildPendingDeleteConversationDialogState(conversationList, 'c-2')).toEqual({
      visible: true,
      conversationId: 'c-2',
      title: '会话二',
    })
  })

  it('builds pending delete dialog state with id fallback title when missing', () => {
    expect(buildPendingDeleteConversationDialogState([], 'c-99')).toEqual({
      visible: true,
      conversationId: 'c-99',
      title: 'c-99',
    })
  })

  it('creates initial delete dialog state', () => {
    expect(createInitialDeleteConversationDialogState()).toEqual({
      visible: false,
      conversationId: null,
      title: '',
    })
  })
})
