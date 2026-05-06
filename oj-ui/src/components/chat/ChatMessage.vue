<template>
  <div class="chat-message" :class="messageClass">
    <div class="message-avatar">
      <span class="avatar-icon">{{ avatarText }}</span>
    </div>
    <div class="message-content">
      <div class="message-bubble" :class="{ 'code-submission': isCodeSubmission }">
        <div v-if="isCodeSubmission" class="submission-badge">已提交</div>
        <div
          v-if="isAgent"
          class="markdown-body"
          v-html="renderedContent"
        />
        <span v-else>{{ message.content }}</span>
      </div>
      <div class="message-timestamp">
        {{ formattedTime }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import type { ConversationMessage } from '@/types/chat'

interface Props {
  message: ConversationMessage
}

const props = defineProps<Props>()

const isAgent = computed(() => props.message.sender === 'AGENT')

const isCodeSubmission = computed(() => props.message.messageType === 'CODE_SUBMISSION')

const messageClass = computed(() =>
  isAgent.value ? 'message-agent' : 'message-user'
)

const avatarText = computed(() =>
  isAgent.value ? 'AI' : '我'
)

const formattedTime = computed(() => {
  if (!props.message.createTime) return ''
  const date = new Date(props.message.createTime)
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
})

// 清理 Markdown 转换后的 HTML，避免脚本注入和危险链接协议。
const sanitizeHtml = (rawHtml: string) => {
  const parser = new DOMParser()
  const documentNode = parser.parseFromString(rawHtml, 'text/html')

  documentNode.querySelectorAll('script,style,iframe,object,embed,link,meta').forEach((node) => {
    node.remove()
  })

  documentNode.querySelectorAll('*').forEach((element) => {
    Array.from(element.attributes).forEach((attribute) => {
      const name = attribute.name.toLowerCase()
      const value = attribute.value.trim()

      if (name.startsWith('on') || name === 'style') {
        element.removeAttribute(attribute.name)
        return
      }

      if ((name === 'href' || name === 'src') && /^(javascript:|data:)/i.test(value)) {
        element.removeAttribute(attribute.name)
      }
    })
  })

  documentNode.querySelectorAll('a').forEach((anchor) => {
    const href = anchor.getAttribute('href') || ''
    const isSafeHref = /^(https?:|mailto:|\/|#)/i.test(href)
    if (!isSafeHref) {
      anchor.removeAttribute('href')
      return
    }
    if (/^https?:/i.test(href)) {
      anchor.setAttribute('target', '_blank')
      anchor.setAttribute('rel', 'noopener noreferrer')
    }
  })

  return documentNode.body.innerHTML
}

const renderedContent = computed(() => {
  try {
    const rawHtml = marked.parse(props.message.content, { breaks: true }) as string
    return sanitizeHtml(rawHtml)
  } catch {
    return sanitizeHtml(props.message.content)
  }
})
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
}

.message-user {
  flex-direction: row-reverse;
}

.message-agent {
  flex-direction: row;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
}

.message-user .avatar-icon {
  background-color: var(--color-primary);
  color: white;
}

.message-agent .avatar-icon {
  background-color: var(--color-background-soft);
  color: var(--color-text);
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 80%;
}

.message-user .message-content {
  align-items: flex-end;
}

.message-agent .message-content {
  align-items: flex-start;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.message-user .message-bubble {
  background-color: var(--color-primary);
  color: white;
}

.message-agent .message-bubble {
  background-color: var(--color-background-soft);
  color: var(--color-text);
}

/* Code submission special styling */
.message-bubble.code-submission {
  border: 2px solid var(--color-primary);
  background-color: var(--color-primary-light, rgba(64, 158, 255, 0.1));
  position: relative;
}

.submission-badge {
  display: inline-block;
  padding: 2px 8px;
  background: var(--color-primary);
  color: white;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 8px;
}

.message-timestamp {
  font-size: 11px;
  color: var(--color-text-secondary);
  padding: 0 4px;
}

/* Markdown 样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 6px;
  font-weight: 600;
  line-height: 1.3;
}

.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 14px; }
.markdown-body :deep(h4) { font-size: 13px; }

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 2px 0;
}

.markdown-body :deep(code) {
  background-color: var(--color-background-mute);
  border-radius: 3px;
  padding: 1px 5px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  background-color: var(--color-background-mute);
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 13px;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--color-border);
  margin: 6px 0;
  padding: 4px 12px;
  color: var(--color-text-secondary);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--color-border);
  margin: 12px 0;
}

.markdown-body :deep(strong) {
  font-weight: 600;
}

.markdown-body :deep(a) {
  color: var(--color-primary);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--color-border);
  padding: 6px 10px;
  text-align: left;
}

.markdown-body :deep(th) {
  background-color: var(--color-background-mute);
  font-weight: 600;
}

/* 第一个和最后一个元素去除多余外边距 */
.markdown-body :deep(> *:first-child) {
  margin-top: 0;
}

.markdown-body :deep(> *:last-child) {
  margin-bottom: 0;
}
</style>
