import type { ChatStreamEvent, StreamIntentType, WorkflowStreamChunk } from '@/types/chat'

/**
 * 流式解析回调接口
 */
export interface StreamParserCallbacks {
  /** 接收到数据块时的回调 */
  onChunk: (event: ChatStreamEvent) => void
  /** 发生错误时的回调 */
  onError: (error: Error) => void
}

const INTENT_NODE_NAME = 'IntentRecognitionNode'
const STREAM_PARSER_DEBUG_ENABLED = import.meta.env.VITE_STREAM_PARSER_DEBUG === 'true'

const debugLog = (...args: unknown[]) => {
  if (STREAM_PARSER_DEBUG_ENABLED) {
    console.log('[StreamParser]', ...args)
  }
}

const debugWarn = (...args: unknown[]) => {
  if (STREAM_PARSER_DEBUG_ENABLED) {
    console.warn('[StreamParser]', ...args)
  }
}

const normalizeIntentType = (value: unknown): StreamIntentType | null => {
  if (value === 'NEW_QUESTION' || value === 'CHANGE_DIFFICULT' || value === 'OTHER') {
    return value
  }

  if (value === 'EVALUATION' || value === 'CODE_EVALUATION') {
    return 'EVALUATION'
  }

  return null
}

const buildChunkKey = (chunk: WorkflowStreamChunk) => {
  return `${chunk.conservationId ?? ''}:${chunk.nodeName ?? 'unknown'}`
}

const buildJsonEvents = (
  conversationId: string,
  nodeName: string,
  parsedPayload: unknown,
  rawChunk: WorkflowStreamChunk,
  timestamp: number
): ChatStreamEvent[] => {
  if (nodeName === INTENT_NODE_NAME) {
    const intentValue =
      parsedPayload && typeof parsedPayload === 'object'
        ? (parsedPayload as Record<string, unknown>).intent
        : undefined
    const normalizedIntent = normalizeIntentType(intentValue)
    if (!normalizedIntent) {
      return []
    }

    return [
      {
        type: 'intent',
        conversationId,
        data: normalizedIntent,
        timestamp,
      },
    ]
  }

  return [
    {
      type: 'structuredResult',
      conversationId,
      data: {
        nodeName,
        raw: rawChunk,
        result: parsedPayload,
      },
      timestamp,
    },
  ]
}

const normalizeJsonChunk = (
  chunk: WorkflowStreamChunk,
  jsonBufferByNode: Map<string, string>
): ChatStreamEvent[] => {
  const key = buildChunkKey(chunk)
  const nextBuffer = `${jsonBufferByNode.get(key) ?? ''}${chunk.text ?? ''}`
  jsonBufferByNode.set(key, nextBuffer)

  let parsedPayload: unknown
  try {
    parsedPayload = JSON.parse(nextBuffer)
  } catch {
    return []
  }

  jsonBufferByNode.delete(key)

  const conversationId = chunk.conservationId ?? ''
  const nodeName = chunk.nodeName ?? 'unknown'
  const timestamp = Date.now()
  const normalizedChunk: WorkflowStreamChunk = {
    ...chunk,
    text: nextBuffer,
  }

  return buildJsonEvents(conversationId, nodeName, parsedPayload, normalizedChunk, timestamp)
}

const normalizeStreamChunk = (
  chunk: WorkflowStreamChunk,
  jsonBufferByNode: Map<string, string>
): ChatStreamEvent[] => {
  const conversationId = chunk.conservationId ?? ''
  const nodeName = chunk.nodeName ?? 'unknown'
  const text = chunk.text ?? ''
  const timestamp = Date.now()

  if (chunk.error) {
    return [
      {
        type: 'error',
        conversationId,
        data: text,
        timestamp,
      },
    ]
  }

  if (chunk.complete) {
    return [
      {
        type: 'complete',
        conversationId,
        data: chunk,
        timestamp,
      },
    ]
  }

  if (!text) {
    return []
  }

  if (nodeName === 'OJAssistantNode') {
    return [
      {
        type: 'assistantToken',
        conversationId,
        data: text,
        timestamp,
      },
    ]
  }

  if (chunk.textType === 'JSON') {
    return normalizeJsonChunk(chunk, jsonBufferByNode)
  }

  if (chunk.textType === 'TEXT') {
    return [
      {
        type: 'overlayText',
        conversationId,
        data: {
          text,
          nodeName,
          raw: chunk,
        },
        timestamp,
      },
    ]
  }

  return []
}

/**
 * 解析流式响应（SSE 格式）
 * @param response - Fetch API 响应对象
 * @param callbacks - 回调函数集合
 */
export async function parseStreamResponse(
  response: Response,
  callbacks: StreamParserCallbacks
): Promise<void> {
  const { onChunk, onError } = callbacks

  // 检查响应体是否存在
  if (!response.body) {
    onError(new Error('Response body is null'))
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const jsonBufferByNode = new Map<string, string>()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        break
      }

      // 解码字节流为文本
      buffer += decoder.decode(value, { stream: true })

      // 按行分割
      const lines = buffer.split('\n')

      // 保留最后一个不完整的行
      buffer = lines.pop() || ''

      // 处理每一行 - SSE 格式解析
      let currentData = ''

      for (const line of lines) {
        const trimmedLine = line.trim()

        // 跳过空行（SSE 事件分隔符）
        if (!trimmedLine) {
          // 如果有累积的数据，处理它
          if (currentData) {
            try {
              const chunk = JSON.parse(currentData) as WorkflowStreamChunk
              debugLog('Parsed SSE chunk:', chunk.nodeName, chunk)
              normalizeStreamChunk(chunk, jsonBufferByNode).forEach(onChunk)
            } catch (parseError) {
              debugWarn('Failed to parse SSE data:', currentData, parseError)
            }
            currentData = ''
          }
          continue
        }

        // 解析 SSE 格式
        if (trimmedLine.startsWith('event:')) {
          continue
        } else if (trimmedLine.startsWith('data:')) {
          const nextData = trimmedLine.substring(5).trim()
          currentData = currentData ? `${currentData}\n${nextData}` : nextData
        } else {
          // 如果不是 SSE 格式，尝试直接解析为 JSON（兼容旧格式）
          try {
            const chunk = JSON.parse(trimmedLine) as WorkflowStreamChunk
            debugLog('Parsed JSON chunk:', chunk.nodeName, chunk)
            normalizeStreamChunk(chunk, jsonBufferByNode).forEach(onChunk)
          } catch (parseError) {
            debugWarn('Failed to parse line:', trimmedLine, parseError)
          }
        }
      }

      // 如果有未处理的数据（没有遇到空行分隔符），保留到下次处理
      if (currentData) {
        buffer = `data:${currentData}\n${buffer}`
      }
    }

    // 处理缓冲区中剩余的数据
    if (buffer.trim()) {
      const trimmedBuffer = buffer.trim()
      if (trimmedBuffer.startsWith('data:')) {
        const data = trimmedBuffer.substring(5).trim()
        try {
          const chunk = JSON.parse(data) as WorkflowStreamChunk
          debugLog('Parsed remaining SSE chunk:', chunk.nodeName, chunk)
          normalizeStreamChunk(chunk, jsonBufferByNode).forEach(onChunk)
        } catch (parseError) {
          debugWarn('Failed to parse remaining buffer:', data, parseError)
        }
      } else {
        try {
          const chunk = JSON.parse(trimmedBuffer) as WorkflowStreamChunk
          debugLog('Parsed remaining JSON chunk:', chunk.nodeName, chunk)
          normalizeStreamChunk(chunk, jsonBufferByNode).forEach(onChunk)
        } catch (parseError) {
          debugWarn('Failed to parse remaining buffer:', trimmedBuffer, parseError)
        }
      }
    }

    // 对于流结束但 JSON 尚未闭合的节点缓冲，按可解析结果补发，避免遗漏后端分片场景。
    jsonBufferByNode.forEach((pendingText, key) => {
      const separatorIndex = key.indexOf(':')
      const conversationId = separatorIndex >= 0 ? key.slice(0, separatorIndex) : ''
      const nodeName = separatorIndex >= 0 ? key.slice(separatorIndex + 1) : key
      try {
        const parsedPayload = JSON.parse(pendingText)
        const normalizedChunk: WorkflowStreamChunk = {
          conservationId: conversationId,
          nodeName,
          textType: 'JSON',
          text: pendingText,
          error: false,
          complete: false,
        }
        buildJsonEvents(
          conversationId,
          nodeName,
          parsedPayload,
          normalizedChunk,
          Date.now()
        ).forEach(onChunk)
      } catch {
        // 忽略无法解析的残留分片，避免误报。
      }
    })
  } catch (error) {
    // 流读取错误
    onError(error instanceof Error ? error : new Error(String(error)))
  } finally {
    // 释放读取器锁
    reader.releaseLock()
  }
}
