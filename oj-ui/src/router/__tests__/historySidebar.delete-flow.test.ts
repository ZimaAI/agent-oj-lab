import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const storage = new Map<string, string>()
const originalLocalStorage = globalThis.localStorage

Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, value)
    },
    removeItem: (key: string) => {
      storage.delete(key)
    },
    clear: () => {
      storage.clear()
    },
  },
  writable: true,
  configurable: true,
})

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')

  return {
    ...actual,
    createWebHistory: () => actual.createMemoryHistory(),
  }
})

import router from '../index'
import {
  resolveNextConversationIdAfterDelete,
  removeConversationFromList,
} from '@/components/layout/HistorySidebar.vue'
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

describe('history sidebar delete route flow', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    localStorage.clear()
    await router.push('/algorithm/conversation/c-2')
    await router.isReady()
  })

  afterEach(async () => {
    storage.clear()
    await router.push('/problems')
  })

  afterAll(() => {
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      writable: true,
      configurable: true,
    })
  })

  it('navigates to next conversation when deleting active conversation and remaining list is not empty', async () => {
    const conversationList = [
      createConversation('c-1', '会话一'),
      createConversation('c-2', '会话二'),
      createConversation('c-3', '会话三'),
    ]

    const nextConversationList = removeConversationFromList(conversationList, 'c-2')
    const nextConversationId = resolveNextConversationIdAfterDelete(
      nextConversationList,
      'c-2',
      String(router.currentRoute.value.params.conversationId),
    )

    if (nextConversationId) {
      await router.push(`/algorithm/conversation/${nextConversationId}`)
    }

    expect(router.currentRoute.value.fullPath).toBe('/algorithm/conversation/c-1')
  })

  it('navigates to /algorithm when deleting active conversation and no remaining item exists', async () => {
    const conversationList = [createConversation('c-2', '会话二')]

    const nextConversationList = removeConversationFromList(conversationList, 'c-2')
    const nextConversationId = resolveNextConversationIdAfterDelete(
      nextConversationList,
      'c-2',
      String(router.currentRoute.value.params.conversationId),
    )

    if (nextConversationId) {
      await router.push(`/algorithm/conversation/${nextConversationId}`)
    } else {
      await router.push('/algorithm')
    }

    expect(router.currentRoute.value.fullPath).toBe('/algorithm')
    expect(router.currentRoute.value.name).toBe('algorithm')
  })

  it('keeps current route when deleting a non-active conversation', async () => {
    const conversationList = [
      createConversation('c-1', '会话一'),
      createConversation('c-2', '会话二'),
    ]

    const nextConversationList = removeConversationFromList(conversationList, 'c-1')
    const nextConversationId = resolveNextConversationIdAfterDelete(
      nextConversationList,
      'c-1',
      String(router.currentRoute.value.params.conversationId),
    )

    if (nextConversationId) {
      await router.push(`/algorithm/conversation/${nextConversationId}`)
    }

    expect(nextConversationId).toBeNull()
    expect(router.currentRoute.value.fullPath).toBe('/algorithm/conversation/c-2')
  })
})
