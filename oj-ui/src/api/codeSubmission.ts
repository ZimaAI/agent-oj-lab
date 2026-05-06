import { parseStreamResponse } from '@/utils/streamParser'
import { getApiBaseUrl } from '@/utils/apiBaseUrl'
import type {
  ChatStreamEvent,
  StreamIntentType,
  WorkflowOverlayPayload,
  WorkflowStructuredResultPayload,
} from '@/types/chat'

const API_BASE_URL = getApiBaseUrl()

/**
 * 代码提交流式请求接口
 */
export interface CodeSubmissionStreamRequest {
  code: string
  algorithmQuestionId: number
  conversationId: string
  language: string
}

/**
 * 流式回调接口
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
 * 流式提交代码
 */
export async function submitCodeStream(
  request: CodeSubmissionStreamRequest,
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

    const url = `${API_BASE_URL}/api/workflow/code-evaluation/stream`

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(request),
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
