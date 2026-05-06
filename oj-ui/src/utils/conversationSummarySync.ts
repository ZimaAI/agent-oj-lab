import type { ConversationListItemVO } from '@/types/chat'

export interface ConversationSummaryUpdate {
  id: string
  title?: string
  currentQuestionId?: number
  lastMessageTime?: string
}

const conversationSummaryEventTarget = new EventTarget()
const conversationSummaryEventName = 'conversation-summary-update'

export function applyConversationSummaryUpdate(
  conversations: ConversationListItemVO[],
  update: ConversationSummaryUpdate,
): ConversationListItemVO[] {
  if (!update.id) {
    return conversations
  }

  let didUpdate = false
  const nextConversations = conversations.map((conversation) => {
    if (conversation.conversationId !== update.id) {
      return conversation
    }

    didUpdate = true
    return {
      ...conversation,
      title: update.title ?? conversation.title,
      currentQuestionId: update.currentQuestionId ?? conversation.currentQuestionId,
      lastMessageTime: update.lastMessageTime ?? conversation.lastMessageTime,
    }
  })

  return didUpdate ? nextConversations : conversations
}

export function emitConversationSummaryUpdate(update: ConversationSummaryUpdate) {
  conversationSummaryEventTarget.dispatchEvent(
    new CustomEvent<ConversationSummaryUpdate>(conversationSummaryEventName, {
      detail: update,
    }),
  )
}

export function subscribeConversationSummaryUpdate(
  listener: (update: ConversationSummaryUpdate) => void,
) {
  const eventListener: EventListener = (event) => {
    listener((event as CustomEvent<ConversationSummaryUpdate>).detail)
  }

  conversationSummaryEventTarget.addEventListener(conversationSummaryEventName, eventListener)

  return () => {
    conversationSummaryEventTarget.removeEventListener(conversationSummaryEventName, eventListener)
  }
}
