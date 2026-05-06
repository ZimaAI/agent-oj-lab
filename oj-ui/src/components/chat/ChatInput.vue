<template>
  <div class="chat-input">
    <div class="input-shell">
      <textarea
        ref="inputTextareaRef"
        v-model="inputText"
        class="input-textarea"
        placeholder="输入消息..."
        aria-label="聊天消息输入框"
        :disabled="disabled"
        @keydown.enter.exact.prevent="handleSend"
        @keydown.enter.shift.exact="handleNewLine"
        rows="1"
      ></textarea>

      <div ref="quickPromptAnchorRef" class="quick-prompt-anchor">
        <button
          ref="quickPromptTriggerRef"
          type="button"
          class="quick-prompt-trigger"
          :class="{ 'is-open': quickPromptMenuOpen }"
          aria-label="快捷指令选择"
          aria-haspopup="menu"
          aria-controls="quick-prompt-menu"
          :aria-expanded="quickPromptMenuOpen"
          :disabled="disabled"
          @click="toggleQuickPromptMenu"
          @keydown="handleQuickPromptTriggerKeydown"
        >
          快捷指令
        </button>

        <ul
          v-if="quickPromptMenuOpen"
          id="quick-prompt-menu"
          class="quick-prompt-menu"
          role="menu"
          aria-label="快捷指令列表"
          @keydown="handleQuickPromptMenuKeydown"
        >
          <li
            v-for="(prompt, index) in quickPromptItems"
            :key="prompt"
            class="quick-prompt-menu-item"
            role="none"
          >
            <button
              type="button"
              ref="quickPromptOptionRefs"
              class="quick-prompt-option"
              role="menuitem"
              :tabindex="activeQuickPromptIndex === index ? 0 : -1"
              @focus="activeQuickPromptIndex = index"
              @click="handleQuickPromptPick(prompt)"
            >
              {{ prompt }}
            </button>
          </li>
        </ul>
      </div>
    </div>

    <button
      class="send-btn"
      :disabled="disabled || !inputText.trim()"
      aria-label="发送消息"
      @click="handleSend"
    >
      发送
    </button>
  </div>
</template>

<script lang="ts">
export const buildQuickPromptItems = () => {
  return [
    '给我一道难度适中的算法题',
    '请帮我提高这道题的难度',
    '请为我讲解这道题的解题思路',
  ]
}

export const mergePromptIntoInput = (currentInput: string, selectedPrompt: string) => {
  const trimmedInput = currentInput.trim()
  if (!trimmedInput) {
    return selectedPrompt
  }

  return `${currentInput}\n${selectedPrompt}`
}

export const handleQuickPromptSelectState = (
  currentInput: string,
  selectedPrompt: string,
  disabled: boolean,
) => {
  if (!selectedPrompt || disabled) {
    return {
      nextInput: currentInput,
      resetSelectValue: false,
      focusInput: false,
    }
  }

  return {
    nextInput: mergePromptIntoInput(currentInput, selectedPrompt),
    resetSelectValue: true,
    focusInput: true,
  }
}
</script>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

interface Props {
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false
})

const emit = defineEmits<{
  send: [content: string]
}>()

const inputText = ref('')
const inputTextareaRef = ref<HTMLTextAreaElement | null>(null)
const quickPromptAnchorRef = ref<HTMLElement | null>(null)
const quickPromptTriggerRef = ref<HTMLButtonElement | null>(null)
const quickPromptMenuOpen = ref(false)
const activeQuickPromptIndex = ref(-1)
const quickPromptOptionRefs = ref<HTMLButtonElement[]>([])
const quickPromptItems = buildQuickPromptItems()

const closeQuickPromptMenu = () => {
  quickPromptMenuOpen.value = false
}

const handleSend = () => {
  if (!inputText.value.trim() || props.disabled) return

  emit('send', inputText.value)
  inputText.value = ''
  closeQuickPromptMenu()
}

const toggleQuickPromptMenu = () => {
  if (props.disabled) {
    return
  }

  quickPromptMenuOpen.value = !quickPromptMenuOpen.value
}

const focusQuickPromptOption = (index: number) => {
  const total = quickPromptItems.length
  if (total <= 0) {
    return
  }

  const normalizedIndex = (index + total) % total
  activeQuickPromptIndex.value = normalizedIndex
  nextTick(() => {
    quickPromptOptionRefs.value[normalizedIndex]?.focus()
  })
}

const handleQuickPromptTriggerKeydown = (event: KeyboardEvent) => {
  if (props.disabled) {
    return
  }

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!quickPromptMenuOpen.value) {
      quickPromptMenuOpen.value = true
    }
    focusQuickPromptOption(activeQuickPromptIndex.value >= 0 ? activeQuickPromptIndex.value : 0)
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!quickPromptMenuOpen.value) {
      quickPromptMenuOpen.value = true
    }
    focusQuickPromptOption(activeQuickPromptIndex.value >= 0 ? activeQuickPromptIndex.value : quickPromptItems.length - 1)
  }
}

const handleQuickPromptMenuKeydown = (event: KeyboardEvent) => {
  if (!quickPromptMenuOpen.value) {
    return
  }

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    focusQuickPromptOption(activeQuickPromptIndex.value + 1)
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    focusQuickPromptOption(activeQuickPromptIndex.value - 1)
    return
  }

  if (event.key === 'Home') {
    event.preventDefault()
    focusQuickPromptOption(0)
    return
  }

  if (event.key === 'End') {
    event.preventDefault()
    focusQuickPromptOption(quickPromptItems.length - 1)
    return
  }

  if (event.key === 'Escape') {
    event.preventDefault()
    closeQuickPromptMenu()
    quickPromptTriggerRef.value?.focus()
  }
}

const handleQuickPromptPick = (selectedPrompt: string) => {
  const nextState = handleQuickPromptSelectState(inputText.value, selectedPrompt, props.disabled)

  inputText.value = nextState.nextInput
  closeQuickPromptMenu()

  if (nextState.focusInput) {
    inputTextareaRef.value?.focus()
  }
}

const handleOutsidePointerDown = (event: MouseEvent) => {
  if (!quickPromptMenuOpen.value) {
    return
  }

  const target = event.target as Node | null
  const anchor = quickPromptAnchorRef.value
  if (!target || !anchor) {
    return
  }

  if (!anchor.contains(target)) {
    closeQuickPromptMenu()
  }
}

const handleEscapeKey = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    closeQuickPromptMenu()
  }
}

const handleNewLine = () => {
  // Allow Shift+Enter to add a new line via native textarea behavior.
}

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) {
      closeQuickPromptMenu()
    }
  }
)

watch(
  () => quickPromptMenuOpen.value,
  (open) => {
    if (open) {
      activeQuickPromptIndex.value = 0
      nextTick(() => {
        quickPromptOptionRefs.value[0]?.focus()
      })
      return
    }

    activeQuickPromptIndex.value = -1
    quickPromptOptionRefs.value = []
  }
)

onMounted(() => {
  document.addEventListener('mousedown', handleOutsidePointerDown)
  document.addEventListener('keydown', handleEscapeKey)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleOutsidePointerDown)
  document.removeEventListener('keydown', handleEscapeKey)
})
</script>

<style scoped>
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
  background: var(--color-background-mute);
}

.input-shell {
  position: relative;
  flex: 1;
  min-width: 0;
}

.input-textarea {
  width: 100%;
  padding: 8px 126px 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-family: inherit;
  resize: none;
  min-height: 36px;
  max-height: 120px;
  background-color: var(--color-background);
  color: var(--color-text);
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.input-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--primary-ring);
  background: var(--color-background-soft);
}

.input-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.quick-prompt-anchor {
  position: absolute;
  right: 8px;
  bottom: 8px;
  z-index: 2;
}

.quick-prompt-trigger {
  min-height: 20px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background-color: var(--color-background-soft);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background-color 0.2s;
}

.quick-prompt-trigger:hover:not(:disabled),
.quick-prompt-trigger.is-open {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.quick-prompt-trigger:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--primary-ring);
}

.quick-prompt-trigger:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.quick-prompt-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 8px);
  width: min(320px, 72vw);
  max-height: 220px;
  overflow-y: auto;
  margin: 0;
  padding: 6px;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-background);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.22);
  z-index: 25;
}

.quick-prompt-menu-item + .quick-prompt-menu-item {
  margin-top: 4px;
}

.quick-prompt-option {
  width: 100%;
  border: none;
  border-radius: calc(var(--radius-sm) - 2px);
  padding: 8px 10px;
  text-align: left;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.3;
  cursor: pointer;
}

.quick-prompt-option:hover,
.quick-prompt-option:focus-visible {
  outline: none;
  background: var(--color-background-soft);
  color: var(--color-primary);
}

.send-btn {
  min-height: 36px;
  padding: 8px 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent), var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, opacity 0.2s, box-shadow 0.2s;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.96;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.send-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .input-textarea {
    padding-right: 114px;
  }

  .quick-prompt-trigger {
    padding: 0 8px;
    font-size: 11px;
  }

  .quick-prompt-menu {
    width: min(260px, 84vw);
  }

  .send-btn {
    padding: 8px 14px;
  }
}
</style>
