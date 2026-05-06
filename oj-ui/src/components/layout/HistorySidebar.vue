<template>
  <div
    class="history-sidebar"
    v-if="!collapsed"
    :style="{ width: `${width}px` }"
    role="complementary"
    aria-label="历史记录侧边栏"
  >
    <div class="sidebar-header">
      <h3>历史记录</h3>
      <button class="collapse-btn" @click="handleToggle" aria-label="收起历史记录侧边栏">
        <span class="icon" aria-hidden="true">«</span>
      </button>
    </div>
    <div class="sidebar-content">
      <button class="create-btn" @click="handleCreateConversation">
        + 创建新对话
      </button>

      <div v-if="loading && conversations.length === 0" class="loading-state">
        <p>加载中...</p>
      </div>

      <div v-else-if="error && conversations.length === 0" class="error-state">
        <p>{{ error }}</p>
        <button @click="() => loadConversationList()" class="retry-btn">重试</button>
      </div>

      <div v-else-if="conversations.length === 0" class="empty-state">
        <p>暂无会话记录</p>
      </div>

      <div v-else class="conversation-list">
        <div
          v-for="conversation in conversations"
          :key="conversation.conversationId"
          class="conversation-item"
          :class="{ active: conversation.conversationId === activeConversationId }"
        >
          <button
            type="button"
            class="conversation-main-button"
            :aria-label="`打开会话 ${conversation.title}`"
            @click="handleConversationClick(conversation.conversationId)"
          >
            <div class="conversation-main">
              <div class="conversation-title">{{ conversation.title }}</div>
              <div class="conversation-time">{{ formatTime(conversation.lastMessageTime || conversation.createTime) }}</div>
            </div>
          </button>
          <button
            class="delete-btn"
            type="button"
            :aria-label="`删除会话 ${conversation.title}`"
            :disabled="deletingConversationId !== null"
            @click.stop="handleOpenDeleteConversationDialog(conversation.conversationId)"
          >
            ×
          </button>
        </div>

        <button
          v-if="hasMore"
          class="load-more-btn"
          @click="loadMore"
          :disabled="loading"
        >
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </div>
  </div>
  <button v-else class="expand-btn" @click="handleToggle" aria-label="展开历史记录侧边栏">
    <span class="icon" aria-hidden="true">»</span>
  </button>
  <div
    v-if="deleteConversationDialogState.visible"
    class="delete-dialog-overlay"
    role="presentation"
    @click="handleCloseDeleteConversationDialog"
    @keydown.esc.prevent="handleCloseDeleteConversationDialog"
  >
    <div
      ref="deleteDialogContentRef"
      class="delete-dialog-content"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-dialog-title"
      @click.stop
    >
      <h4 id="delete-dialog-title" class="delete-dialog-title">确认删除会话</h4>
      <p class="delete-dialog-message">
        {{ buildDeleteConversationConfirmText(deleteConversationDialogState.title) }}
      </p>
      <div class="delete-dialog-actions">
        <button
          ref="deleteDialogCancelButtonRef"
          type="button"
          class="delete-dialog-cancel-btn"
          :disabled="deletingConversationId !== null"
          @click="handleCloseDeleteConversationDialog"
        >
          取消
        </button>
        <button
          type="button"
          class="delete-dialog-confirm-btn"
          :disabled="deletingConversationId !== null"
          @click="handleConfirmDeleteConversation"
        >
          {{ deletingConversationId !== null ? '删除中...' : '删除' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import type { ConversationListItemVO } from '@/types/chat'

export const buildDeleteConversationConfirmText = (title: string) => {
  return `确认删除会话“${title}”吗？`
}

export const resolveDeleteConversationTitle = (
  conversationList: ConversationListItemVO[],
  conversationId: string,
) => {
  const matchedConversation = conversationList.find(item => item.conversationId === conversationId)
  return matchedConversation?.title || conversationId
}

export interface DeleteConversationDialogState {
  visible: boolean
  conversationId: string | null
  title: string
}

export const buildPendingDeleteConversationDialogState = (
  conversationList: ConversationListItemVO[],
  conversationId: string,
): DeleteConversationDialogState => {
  return {
    visible: true,
    conversationId,
    title: resolveDeleteConversationTitle(conversationList, conversationId),
  }
}

export const createInitialDeleteConversationDialogState = (): DeleteConversationDialogState => {
  return {
    visible: false,
    conversationId: null,
    title: '',
  }
}

export const removeConversationFromList = (
  conversationList: ConversationListItemVO[],
  conversationId: string,
) => {
  return conversationList.filter(item => item.conversationId !== conversationId)
}

export const resolveNextConversationIdAfterDelete = (
  conversationList: ConversationListItemVO[],
  deletedConversationId: string,
  currentActiveConversationId?: string,
): string | null => {
  if (currentActiveConversationId !== deletedConversationId) {
    return null
  }

  return conversationList[0]?.conversationId ?? null
}
</script>

<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getConversationList, createConversation, deleteConversation } from '@/api/conversation'
import {
  applyConversationSummaryUpdate,
  subscribeConversationSummaryUpdate,
} from '@/utils/conversationSummarySync'

interface Props {
  collapsed: boolean
  width: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  toggle: []
}>()

const router = useRouter()
const route = useRoute()

const activeConversationId = computed(() =>
  route.params.conversationId ? String(route.params.conversationId) : undefined
)

// State
const conversations = ref<ConversationListItemVO[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const deletingConversationId = ref<string | null>(null)
const deleteDialogCancelButtonRef = ref<HTMLButtonElement | null>(null)
const deleteDialogContentRef = ref<HTMLElement | null>(null)
const lastFocusedElementBeforeDeleteDialog = ref<HTMLElement | null>(null)
const deleteConversationDialogState = ref<DeleteConversationDialogState>(
  createInitialDeleteConversationDialogState(),
)

// Computed
const hasMore = computed(() => conversations.value.length < total.value)

// Methods
const loadConversationList = async (page = 1) => {
  try {
    loading.value = true
    error.value = null

    const response = await getConversationList({
      current: page,
      pageSize: pageSize.value
    })

    if (page === 1) {
      conversations.value = response.records
    } else {
      conversations.value.push(...response.records)
    }

    total.value = response.total
    currentPage.value = page
  } catch (err: any) {
    error.value = err.message || '加载会话列表失败'
    console.error('Failed to load conversation list:', err)
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  if (!loading.value && hasMore.value) {
    loadConversationList(currentPage.value + 1)
  }
}

const handleCreateConversation = async () => {
  try {
    const response = await createConversation()
    await router.push(`/algorithm/conversation/${response.conversationId}`)
    await loadConversationList(1)
  } catch (err: any) {
    error.value = err.message || '创建会话失败'
    console.error('Failed to create conversation:', err)
  }
}

const handleConversationClick = (conversationId: string) => {
  router.push(`/algorithm/conversation/${conversationId}`)
}

const handleOpenDeleteConversationDialog = (conversationId: string) => {
  if (deletingConversationId.value !== null) {
    return
  }

  const activeElement = document.activeElement
  lastFocusedElementBeforeDeleteDialog.value =
    activeElement instanceof HTMLElement ? activeElement : null

  deleteConversationDialogState.value = buildPendingDeleteConversationDialogState(
    conversations.value,
    conversationId,
  )
}

const handleCloseDeleteConversationDialog = () => {
  if (deletingConversationId.value !== null) {
    return
  }

  deleteConversationDialogState.value = createInitialDeleteConversationDialogState()
  nextTick(() => {
    lastFocusedElementBeforeDeleteDialog.value?.focus()
  })
}

const handleConfirmDeleteConversation = async () => {
  const conversationId = deleteConversationDialogState.value.conversationId
  if (!conversationId || deletingConversationId.value !== null) {
    return
  }

  deletingConversationId.value = conversationId
  try {
    await deleteConversation(conversationId)

    const nextConversationList = removeConversationFromList(conversations.value, conversationId)
    const nextConversationId = resolveNextConversationIdAfterDelete(
      nextConversationList,
      conversationId,
      activeConversationId.value,
    )

    if (nextConversationId) {
      await router.push(`/algorithm/conversation/${nextConversationId}`)
    } else if (activeConversationId.value === conversationId) {
      await router.push('/algorithm')
    }

    conversations.value = nextConversationList
    await loadConversationList(1)
  } catch (err: unknown) {
    error.value = err instanceof Error ? err.message : '删除会话失败'
    console.error('Failed to delete conversation:', err)
  } finally {
    deletingConversationId.value = null
    deleteConversationDialogState.value = createInitialDeleteConversationDialogState()
  }
}

const getLocalTimezoneOffset = () => {
  const offset = -new Date().getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  const h = String(Math.floor(abs / 60)).padStart(2, '0')
  const m = String(abs % 60).padStart(2, '0')
  return `${sign}${h}:${m}`
}

const formatTime = (time: string) => {
  if (!time) return ''
  // LocalDateTime 序列化后不含时区（如 "2026-03-16T10:00:00"），
  // 部分浏览器会按 UTC 解析，需要在末尾追加当前时区偏移，
  // 或直接将 T/空格 替换处理后让浏览器按本地时间解析。
  // 最稳妥的方式：将 "2026-03-16 10:00:00" 或 "2026-03-16T10:00:00"
  // 统一解析为本地时间。
  const normalized = time.replace(' ', 'T')
  // 若字符串不含时区标记，追加本地时区偏移，确保按本地时间解析
  const hasTimezone = /[Z+\-]\d{2}:?\d{2}$/.test(normalized) || normalized.endsWith('Z')
  const dateStr = hasTimezone ? normalized : normalized + getLocalTimezoneOffset()
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    const hours = Math.floor(diff / (1000 * 60 * 60))
    if (hours === 0) {
      const minutes = Math.floor(diff / (1000 * 60))
      return minutes === 0 ? '刚刚' : `${minutes}分钟前`
    }
    return `${hours}小时前`
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  }
}

const handleToggle = () => {
  emit('toggle')
}

const stopConversationSummarySync = subscribeConversationSummaryUpdate((update) => {
  const nextConversations = applyConversationSummaryUpdate(conversations.value, update)

  if (nextConversations === conversations.value) {
    if (activeConversationId.value === update.id) {
      loadConversationList(1)
    }
    return
  }

  conversations.value = nextConversations
})

// 弹窗打开时将焦点放在取消按钮上，确保键盘用户可立即操作。
watch(
  () => deleteConversationDialogState.value.visible,
  (visible) => {
    if (visible) {
      nextTick(() => {
        deleteDialogCancelButtonRef.value?.focus()
      })
    }
  }
)

const handleDocumentKeydown = (event: KeyboardEvent) => {
  if (!deleteConversationDialogState.value.visible) {
    return
  }

  if (event.key === 'Escape') {
    handleCloseDeleteConversationDialog()
    return
  }

  // 删除弹窗打开时锁定焦点在弹窗内部，避免 Tab 跳出对话框。
  if (event.key !== 'Tab') {
    return
  }

  const dialogElement = deleteDialogContentRef.value
  if (!dialogElement) {
    return
  }

  const focusableElements = Array.from(
    dialogElement.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
  )

  if (focusableElements.length === 0) {
    return
  }

  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]
  if (!firstElement || !lastElement) {
    return
  }
  const activeElement = document.activeElement as HTMLElement | null

  if (event.shiftKey) {
    if (activeElement === firstElement || !dialogElement.contains(activeElement)) {
      event.preventDefault()
      lastElement.focus()
    }
    return
  }

  if (activeElement === lastElement) {
    event.preventDefault()
    firstElement.focus()
  }
}

// Lifecycle
onMounted(() => {
  loadConversationList()
  document.addEventListener('keydown', handleDocumentKeydown)
})

onUnmounted(() => {
  stopConversationSummarySync()
  document.removeEventListener('keydown', handleDocumentKeydown)
})

// When a new conversation is navigated to (e.g., created by ProblemDetailView),
// refresh the list if it's not already in our local cache
watch(activeConversationId, (newId) => {
  if (newId && !conversations.value.some(c => c.conversationId === newId)) {
    loadConversationList(1)
  }
})
</script>

<style scoped>
.history-sidebar {
  height: 100%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background-color: var(--color-background);
  border-right: 1px solid var(--color-border);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.02);
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-background-mute);
}

.sidebar-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.collapse-btn,
.expand-btn {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  min-width: 30px;
  min-height: 30px;
  padding: 4px 7px;
  color: var(--color-text-secondary);
  transition: color 0.2s, border-color 0.2s, background 0.2s, transform 0.2s;
}

.collapse-btn:hover,
.expand-btn:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-primary-dim);
  transform: translateY(-1px);
}

.collapse-btn:focus,
.expand-btn:focus {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.icon {
  font-size: 16px;
  font-weight: bold;
}

.sidebar-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.create-btn {
  width: 100%;
  min-height: 38px;
  padding: 10px 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), transparent), var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}

.create-btn:hover {
  background-color: var(--color-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.create-btn:focus-visible,
.retry-btn:focus-visible,
.load-more-btn:focus-visible,
.delete-dialog-cancel-btn:focus-visible,
.delete-dialog-confirm-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.create-btn:active {
  transform: scale(0.98);
}

.loading-state,
.error-state,
.empty-state {
  text-align: center;
  padding: 40px 16px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.error-state {
  color: var(--color-error);
}

.retry-btn {
  margin-top: 12px;
  padding: 6px 16px;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}

.retry-btn:hover {
  background-color: var(--color-background-mute);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 12px;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: background-color 0.2s, border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}

.conversation-item:hover,
.conversation-item:focus-within {
  background-color: var(--color-background-mute);
  border-color: var(--color-primary);
  transform: translateX(2px);
  box-shadow: var(--shadow-sm);
}

.conversation-item.active {
  background-color: var(--color-background-mute);
  border-color: var(--color-primary);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.conversation-item.active .conversation-title {
  color: var(--color-primary);
  font-weight: 600;
}

.conversation-main {
  flex: 1;
  min-width: 0;
}

.conversation-main-button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
}

.conversation-main-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
  border-radius: var(--radius-sm);
}

.conversation-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.delete-btn {
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 18px;
  line-height: 1;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  cursor: pointer;
  flex-shrink: 0;
}

.delete-btn:hover {
  color: var(--color-error);
  background: rgba(239, 68, 68, 0.12);
}

.delete-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.load-more-btn {
  width: 100%;
  min-height: 34px;
  padding: 8px 16px;
  margin-top: 8px;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}

.load-more-btn:hover:not(:disabled) {
  background-color: var(--color-background-mute);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.load-more-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.delete-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
}

.delete-dialog-content {
  width: min(420px, calc(100vw - 32px));
  padding: 20px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-background);
  box-shadow: var(--shadow-sm);
}

.delete-dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
}

.delete-dialog-message {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  word-break: break-word;
}

.delete-dialog-actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.delete-dialog-cancel-btn,
.delete-dialog-confirm-btn {
  min-height: 34px;
  min-width: 84px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s, opacity 0.2s;
}

.delete-dialog-cancel-btn {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.delete-dialog-cancel-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.delete-dialog-confirm-btn {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #ef4444;
}

.delete-dialog-confirm-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.2);
}

.delete-dialog-cancel-btn:disabled,
.delete-dialog-confirm-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.expand-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-left: none;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  padding: 8px 4px;
}
</style>
