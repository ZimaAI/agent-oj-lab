export interface PersistedQuestionResolverOptions {
  streamedQuestionId?: number
  currentQuestionId?: number
  conversationId?: string
  requireConversationSync?: boolean
  maxAttempts?: number
  delayMs?: number
  wait?: (ms: number) => Promise<void>
  loadConversationSummary: (conversationId: string) => Promise<{ currentQuestionId?: number | null } | undefined>
}

const defaultWait = (ms: number) =>
  new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })

const isPersistedQuestionId = (questionId?: number | null): questionId is number =>
  typeof questionId === 'number' && questionId > 0

export async function resolvePersistedQuestionId({
  streamedQuestionId,
  currentQuestionId,
  conversationId,
  requireConversationSync = false,
  maxAttempts = 5,
  delayMs = 100,
  wait = defaultWait,
  loadConversationSummary,
}: PersistedQuestionResolverOptions): Promise<number | undefined> {
  const preferredQuestionId = isPersistedQuestionId(streamedQuestionId)
    ? streamedQuestionId
    : isPersistedQuestionId(currentQuestionId)
      ? currentQuestionId
      : undefined

  if (!conversationId) {
    return preferredQuestionId
  }

  if (!requireConversationSync && isPersistedQuestionId(preferredQuestionId)) {
    return preferredQuestionId
  }

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const conversation = await loadConversationSummary(conversationId)
    const persistedQuestionId = conversation?.currentQuestionId
    if (isPersistedQuestionId(persistedQuestionId)) {
      if (!requireConversationSync || !isPersistedQuestionId(preferredQuestionId) || persistedQuestionId === preferredQuestionId) {
        return persistedQuestionId
      }
    }

    if (attempt < maxAttempts - 1) {
      await wait(delayMs)
    }
  }

  return requireConversationSync ? undefined : preferredQuestionId
}
