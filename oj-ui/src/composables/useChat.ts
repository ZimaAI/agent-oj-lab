import { ref } from 'vue'
import type { ChatMessage } from '@/types/chat'
import { MessageSender } from '@/types/chat'

export function useChat() {
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)

  const sendMessage = async (content: string) => {
    if (!content.trim()) return

    // Add user message
    const userMessage: ChatMessage = {
      id: `msg-${Date.now()}-user`,
      sender: MessageSender.USER,
      content: content.trim(),
      timestamp: Date.now()
    }
    messages.value.push(userMessage)

    // Simulate Agent response (mock for now)
    loading.value = true
    try {
      await new Promise(resolve => setTimeout(resolve, 1000))

      const agentMessage: ChatMessage = {
        id: `msg-${Date.now()}-agent`,
        sender: MessageSender.AGENT,
        content: '这是一个模拟的 Agent 回复。实际的 Agent 集成将在后续实现。',
        timestamp: Date.now()
      }
      messages.value.push(agentMessage)
    } catch (e) {
      console.error('Failed to send message:', e)
    } finally {
      loading.value = false
    }
  }

  const clearMessages = () => {
    messages.value = []
  }

  return {
    messages,
    loading,
    sendMessage,
    clearMessages
  }
}
