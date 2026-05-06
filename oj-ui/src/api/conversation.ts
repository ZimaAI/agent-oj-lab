import { http } from '@/utils/http'
import { getApiBaseUrl } from '@/utils/apiBaseUrl'
import type {
  ChatStreamEvent,
  ConversationCreateVO,
  ConversationListItemVO,
  ConversationListResponse,
  MessageListResponse,
  StreamIntentType,
  WorkflowOverlayPayload,
  WorkflowStructuredResultPayload,
} from '@/types/chat'
import { parseStreamResponse } from '@/utils/streamParser'

const API_BASE_URL = getApiBaseUrl()
const CONVERSATIONS_BASE_PATH = '/api/conversations'

/**
 * 创建会话
 */
export function createConversation(params?: { currentQuestionId?: number; questionId?: number }) {
  const currentQuestionId = params?.currentQuestionId ?? params?.questionId
  const payload = currentQuestionId === undefined ? {} : { currentQuestionId }
  return http.post<ConversationCreateVO>(CONVERSATIONS_BASE_PATH, payload)
}

/**
 * 获取会话列表
 */
export function getConversationList(params: { current: number; pageSize: number }) {
  return http.get<ConversationListResponse>(CONVERSATIONS_BASE_PATH, {
    params: {
      current: params.current,
      pageSize: params.pageSize
    }
  })
}

/**
 * 通过分页会话列表查找指定会话摘要
 */
export async function getConversationSummaryById(
  conversationId: string,
  options: { pageSize?: number } = {}
): Promise<ConversationListItemVO | undefined> {
  const pageSize = options.pageSize ?? 20
  let current = 1
  let pages = 1

  while (current <= pages) {
    const response = await getConversationList({ current, pageSize })
    const matchedConversation = response.records.find(
      (conversation) => conversation.conversationId === conversationId
    )

    if (matchedConversation) {
      return matchedConversation
    }

    pages = response.pages || 1
    current += 1
  }

  return undefined
}

/**
 * 获取消息列表
 */
export function getMessageList(conversationId: string, params: { current: number; pageSize: number }) {
  return http.get<MessageListResponse>(`${CONVERSATIONS_BASE_PATH}/${conversationId}/messages`, {
    params: {
      current: params.current,
      pageSize: params.pageSize
    }
  })
}

/**
 * 删除会话
 */
export function deleteConversation(conversationId: string) {
  return http.delete<void>(`${CONVERSATIONS_BASE_PATH}/${conversationId}`)
}

/**
 * 流式发送消息回调接口
 */
export interface StreamCallbacks {
  onAssistantToken: (data: string) => void
  onOverlayText: (payload: WorkflowOverlayPayload) => void
  onStructuredResult: (payload: WorkflowStructuredResultPayload) => void
  onIntent: (intent: StreamIntentType) => void
  onComplete: () => void
  onError: (error: string) => void
}

/**
 * 流式发送消息
 */
export async function sendMessageStream(
  conversationId: string,
  message: string,
  callbacks: StreamCallbacks,
  abortSignal?: AbortSignal
): Promise<void> {
  try {
    const token = localStorage.getItem('access_token')
    const headers: HeadersInit = {
      'Content-Type': 'application/json'
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const url = `${API_BASE_URL}/api/workflow/stream`

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        conversationId,
        message
      }),
      signal: abortSignal
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    await parseStreamResponse(response, {
      onChunk: (event: ChatStreamEvent) => {
        switch (event.type) {
          case 'assistantToken':
            if (event.data) {
              callbacks.onAssistantToken(event.data)
            }
            break
          case 'overlayText':
            callbacks.onOverlayText(event.data)
            break
          case 'structuredResult':
            callbacks.onStructuredResult(event.data)
            break
          case 'intent':
            callbacks.onIntent(event.data)
            break
          case 'complete':
            callbacks.onComplete()
            break
          case 'error':
            callbacks.onError((event.data as string) || '未知错误')
            break
        }
      },
      onError: (error: Error) => {
        callbacks.onError(error.message)
      }
    })
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      return
    }
    callbacks.onError(error instanceof Error ? error.message : '未知错误')
  }
}
