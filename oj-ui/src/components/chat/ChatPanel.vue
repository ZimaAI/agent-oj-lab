<template>
  <div class="chat-panel">
    <div class="chat-header">
      <h3>AI 助手</h3>
      <button class="collapse-btn" type="button" aria-label="收起聊天面板" @click="handleCollapse">
        <span class="icon">×</span>
      </button>
    </div>

    <div class="chat-messages" ref="messagesContainer" aria-live="polite" aria-relevant="additions text">
      <div v-if="loadingHistory" class="loading-history">
        <p>加载历史消息...</p>
      </div>

      <button
        v-if="hasMoreMessages && !loadingHistory"
        class="load-more-messages-btn"
        type="button"
        @click="loadMoreMessages"
      >
        加载更早的消息
      </button>

      <div v-if="error" class="error-message">
        <p>{{ error }}</p>
        <button @click="retryLoadHistory" class="retry-btn">重试</button>
      </div>

      <div v-if="messages.length === 0 && !loadingHistory" class="welcome-message">
        <span class="welcome-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M8 10V8.5C8 6.57 9.57 5 11.5 5H12.5C14.43 5 16 6.57 16 8.5V10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <rect x="5" y="10" width="14" height="9" rx="3" stroke="currentColor" stroke-width="1.8" />
            <circle cx="10" cy="14.5" r="1" fill="currentColor" />
            <circle cx="14" cy="14.5" r="1" fill="currentColor" />
          </svg>
        </span>
        <p>你好！我是 AI 助手</p>
        <p>有什么可以帮助你的吗？</p>
      </div>

      <template v-for="message in messages" :key="message.id">
        <div
          v-if="displayOverlayText && latestAgentMessageId !== null && message.id === latestAgentMessageId"
          class="node-hint-card"
        >
          <div class="node-hint-label">节点提示</div>
          <div class="node-hint-text">{{ displayOverlayText }}</div>
          <div v-if="displayOverlayNodeName" class="node-hint-node">{{ displayOverlayNodeName }}</div>
        </div>

        <ChatMessage :message="message" />
      </template>

      <div v-if="sendingMessage" class="typing-indicator">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>

    <ChatInput @send="handleSend" :disabled="sendingMessage || !conversationId" />
  </div>
</template>

<script lang="ts">
import type { ConversationMessage as ChatConversationMessage } from '@/types/chat'

// 按消息时序统一排序，优先使用 sequenceNo，缺失时回退到 createTime。
export const sortMessagesChronologically = (
  messageList: ChatConversationMessage[]
): ChatConversationMessage[] => {
  return [...messageList].sort((left, right) => {
    const leftSequenceNo = typeof left.sequenceNo === 'number' ? left.sequenceNo : Number.POSITIVE_INFINITY
    const rightSequenceNo = typeof right.sequenceNo === 'number' ? right.sequenceNo : Number.POSITIVE_INFINITY

    if (leftSequenceNo !== rightSequenceNo) {
      return leftSequenceNo - rightSequenceNo
    }

    const leftCreateTime = new Date(left.createTime).getTime()
    const rightCreateTime = new Date(right.createTime).getTime()

    if (leftCreateTime !== rightCreateTime) {
      return leftCreateTime - rightCreateTime
    }

    // createTime 相同时回退到字符串 id，避免非数字 id 产生 NaN 导致排序不稳定。
    return String(left.id ?? '').localeCompare(String(right.id ?? ''))
  })
}

export const findLatestAgentMessageId = (
  messageList: ChatConversationMessage[]
): string | number | null => {
  for (let index = messageList.length - 1; index >= 0; index -= 1) {
    if (messageList[index]?.sender === 'AGENT') {
      return messageList[index]?.id ?? null
    }
  }
  return null
}
</script>

<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted, computed } from 'vue'
import type {
  ConversationMessage,
  StreamIntentType,
  WorkflowOverlayPayload,
  WorkflowStructuredResultPayload,
} from '@/types/chat'
import { getMessageList, sendMessageStream } from '@/api/conversation'
import { throttle } from '@/utils/throttle'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'

interface Props {
  conversationId?: string
  externalOverlayText?: string
  externalOverlayNodeName?: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  collapse: []
  intent: [intent: StreamIntentType]
  overlayText: [payload: WorkflowOverlayPayload]
  structuredResult: [payload: WorkflowStructuredResultPayload]
  streamComplete: []
  streamError: [message: string]
}>()

const messagesContainer = ref<HTMLElement | null>(null)
const messages = ref<ConversationMessage[]>([])
const loadingHistory = ref(false)
const sendingMessage = ref(false)
const error = ref<string | null>(null)
const currentPage = ref(1)
const pageSize = ref(50)
const total = ref(0)

const streamingMessage = ref<ConversationMessage | null>(null)
const tokenBuffer = ref<string>('')
const overlayText = ref('')
const overlayNodeName = ref<string | null>(null)
const abortController = ref<AbortController | null>(null)

const hasMoreMessages = computed(() => messages.value.length < total.value)
const latestAgentMessageId = computed(() => findLatestAgentMessageId(messages.value))
const displayOverlayText = computed(() => {
  return props.externalOverlayText ?? overlayText.value
})
const displayOverlayNodeName = computed(() => {
  return props.externalOverlayNodeName ?? overlayNodeName.value
})

const updateStreamingMessage = () => {
  if (streamingMessage.value && tokenBuffer.value) {
    streamingMessage.value.content += tokenBuffer.value
    tokenBuffer.value = ''
  }
}

const throttledUpdateToken = throttle(updateStreamingMessage, 100)

// Load message history
const loadMessageHistory = async (page = 1) => {
  if (!props.conversationId) return

  try {
    loadingHistory.value = true
    error.value = null

    const response = await getMessageList(props.conversationId, {
      current: page,
      pageSize: pageSize.value
    })
    const sortedPageMessages = sortMessagesChronologically(response.records)

    if (page === 1) {
      // 首次加载时保证历史消息按时间正序展示。
      messages.value = sortedPageMessages
    } else {
      // 追加更早页后再次归一化，避免后端分页结果乱序影响展示。
      messages.value = sortMessagesChronologically([...sortedPageMessages, ...messages.value])
    }

    total.value = response.total
    currentPage.value = page
  } catch (err: any) {
    error.value = err.message || '加载消息历史失败'
    console.error('Failed to load message history:', err)
  } finally {
    loadingHistory.value = false
  }
}

const loadMoreMessages = () => {
  if (!loadingHistory.value && hasMoreMessages.value) {
    const scrollHeightBefore = messagesContainer.value?.scrollHeight || 0
    loadMessageHistory(currentPage.value + 1).then(() => {
      // Maintain scroll position after prepending messages
      nextTick(() => {
        if (messagesContainer.value) {
          const scrollHeightAfter = messagesContainer.value.scrollHeight
          messagesContainer.value.scrollTop = scrollHeightAfter - scrollHeightBefore
        }
      })
    })
  }
}

const retryLoadHistory = () => {
  loadMessageHistory(1)
}

// Send message
const handleSend = async (content: string) => {
  if (!props.conversationId || sendingMessage.value) return

  // Cancel previous request if exists
  if (abortController.value) {
    abortController.value.abort()
  }

  // Store message IDs for cleanup
  const userMessageId = Date.now()
  const agentMessageId = userMessageId + 1

  try {
    sendingMessage.value = true
    error.value = null
    abortController.value = new AbortController()

    // Add user message optimistically
    const userMessage: ConversationMessage = {
      id: userMessageId,
      conversationId: props.conversationId,
      sender: 'USER',
      content,
      resultType: undefined,
      resultData: undefined,
      createTime: new Date().toISOString()
    }
    messages.value.push(userMessage)

    // Create empty agent message placeholder
    const agentMessage: ConversationMessage = {
      id: agentMessageId,
      conversationId: props.conversationId,
      sender: 'AGENT',
      content: '',
      resultType: undefined,
      resultData: undefined,
      createTime: new Date().toISOString()
    }
    messages.value.push(agentMessage)
    streamingMessage.value = agentMessage

    // Call streaming API
    await sendMessageStream(
      props.conversationId,
      content,
      {
        onAssistantToken: (token: string) => {
          tokenBuffer.value += token
          throttledUpdateToken()
        },
        onOverlayText: (payload) => {
          overlayText.value = payload.text
          overlayNodeName.value = payload.nodeName
          emit('overlayText', payload)
        },
        onStructuredResult: (payload) => {
          overlayText.value = ''
          overlayNodeName.value = null
          emit('structuredResult', payload)
        },
        onIntent: (intent: StreamIntentType) => {
          emit('intent', intent)
        },
        onComplete: () => {
          // Flush remaining tokens
          if (tokenBuffer.value) {
            updateStreamingMessage()
          }

          // Note: We don't have the final response with IDs here
          // The streaming message will remain with temporary ID
          // This is acceptable as it shows the streamed content

          // Clear streaming state
          streamingMessage.value = null
          tokenBuffer.value = ''
          overlayText.value = ''
          overlayNodeName.value = null
          abortController.value = null
          emit('streamComplete')
        },
        onError: (errorMsg: string) => {
          // Display error in message
          if (streamingMessage.value) {
            streamingMessage.value.content = `错误: ${errorMsg}`
          }
          error.value = errorMsg || '发送消息失败'
          console.error('Failed to send message:', errorMsg)

          // Clear streaming state
          streamingMessage.value = null
          tokenBuffer.value = ''
          overlayText.value = ''
          overlayNodeName.value = null
          abortController.value = null
          emit('streamError', errorMsg)
        }
      },
      abortController.value.signal
    )
  } catch (err: any) {
    if (err.name === 'AbortError') {
      // Remove optimistic messages on abort
      messages.value = messages.value.filter(
        m => m.id !== userMessageId && m.id !== agentMessageId
      )
    } else {
      error.value = err.message || '发送消息失败'
      console.error('Failed to send message:', err)
      // Remove optimistic messages on error
      messages.value = messages.value.filter(
        m => m.id !== userMessageId && m.id !== agentMessageId
      )
    }
    // Clear streaming state
    streamingMessage.value = null
    tokenBuffer.value = ''
    overlayText.value = ''
    overlayNodeName.value = null
    abortController.value = null
    emit('streamError', err.message || '发送消息失败')
  } finally {
    sendingMessage.value = false
  }
}

const handleCollapse = () => {
  emit('collapse')
}

// Auto-scroll to latest message
const scrollToBottom = () => {
  if (messagesContainer.value) {
    nextTick(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    })
  }
}

// Watch for new messages
watch(() => messages.value.length, (newLength, oldLength) => {
  if (newLength > oldLength) {
    scrollToBottom()
  }
})

watch(() => sendingMessage.value, (newSending) => {
  if (newSending) {
    scrollToBottom()
  }
})

// Load history when conversationId changes
watch(() => props.conversationId, (newId) => {
  // Abort ongoing request
  if (abortController.value) {
    abortController.value.abort()
  }

  // Clear streaming state
  streamingMessage.value = null
  tokenBuffer.value = ''
  overlayText.value = ''
  overlayNodeName.value = null
  sendingMessage.value = false

  if (newId) {
    messages.value = []
    currentPage.value = 1
    total.value = 0
    loadMessageHistory(1)
  }
}, { immediate: true })

onUnmounted(() => {
  if (abortController.value) {
    abortController.value.abort()
  }
})

// Expose methods for external components
defineExpose({
  addMessage: (message: ConversationMessage) => {
    messages.value.push(message)
  },
  startStreaming: (message: ConversationMessage) => {
    messages.value.push(message)
    streamingMessage.value = message
    sendingMessage.value = true
  },
  updateStreamingToken: (token: string) => {
    tokenBuffer.value += token
    throttledUpdateToken()
  },
  completeStreaming: () => {
    // Flush remaining tokens
    if (tokenBuffer.value) {
      updateStreamingMessage()
    }
    // Clear streaming state
    streamingMessage.value = null
    tokenBuffer.value = ''
    sendingMessage.value = false
    abortController.value = null
  },
  errorStreaming: (errorMsg: string) => {
    // Display error in message
    if (streamingMessage.value) {
      streamingMessage.value.content = `错误: ${errorMsg}`
    }
    // Clear streaming state
    streamingMessage.value = null
    tokenBuffer.value = ''
    sendingMessage.value = false
    abortController.value = null
  }
})
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-background-soft);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent), var(--color-background-mute);
}

.chat-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.collapse-btn {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  min-width: 30px;
  min-height: 30px;
  padding: 4px 8px;
  color: var(--color-text-secondary);
  transition: color 0.2s, border-color 0.2s, background 0.2s, transform 0.2s;
  font-size: 20px;
  line-height: 1;
}

.collapse-btn:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-primary-dim);
  transform: translateY(-1px);
}

.collapse-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.015), transparent);
}

.loading-history {
  text-align: center;
  padding: 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.load-more-messages-btn {
  align-self: center;
  min-height: 32px;
  padding: 6px 16px;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}

.load-more-messages-btn:hover {
  background-color: var(--color-background-mute);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.load-more-messages-btn:focus-visible,
.retry-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.error-message {
  text-align: center;
  padding: 12px;
  color: var(--color-error);
  font-size: 13px;
}

.retry-btn {
  margin-top: 8px;
  padding: 4px 12px;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}

.retry-btn:hover {
  background-color: var(--color-background-mute);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  text-align: center;
  color: var(--color-text-secondary);
  margin-top: 40px;
}

.welcome-icon {
  width: 26px;
  height: 26px;
  margin: 0;
  color: var(--color-primary);
}

.welcome-icon svg {
  width: 100%;
  height: 100%;
}

.welcome-message p {
  margin: 0;
  font-size: 14px;
}

.node-hint-card {
  align-self: flex-start;
  max-width: 80%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent), var(--color-background-mute);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.node-hint-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.node-hint-text {
  font-size: 14px;
  color: var(--color-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.node-hint-node {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px;
  align-self: flex-start;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background-color: var(--color-primary);
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-8px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .typing-indicator span {
    animation: none;
  }
}
</style>
