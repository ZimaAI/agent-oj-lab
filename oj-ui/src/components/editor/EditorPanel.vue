<template>
  <div class="editor-panel">
    <div class="editor-header">
      <div class="editor-controls">
        <select v-model="selectedLanguage" class="language-selector">
          <option
            v-for="language in languageOptions"
            :key="language"
            :value="language"
          >
            {{ languageLabelMap[language] }}
          </option>
        </select>
        <button class="btn-run" @click="handleRun" :disabled="isRunDisabled">
          <span v-if="!runLoading">▶ 运行</span>
          <span v-else>运行中...</span>
        </button>
        <button class="btn-submit" @click="handleSubmit" :disabled="isSubmitDisabled">
          <span>✓ 提交</span>
        </button>
      </div>
      <p class="shortcut-hint">Ctrl/Cmd + Enter 运行 | Ctrl/Cmd + Shift + Enter 提交</p>
    </div>

    <div class="editor-body" ref="bodyRef" @keydown.capture="handleEditorShortcut">
      <!-- 上栏：代码编辑器 -->
      <div class="editor-content" :style="{ height: editorHeight + 'px' }">
        <CodeEditor
          :key="`${problemId ?? 'no-problem'}_${selectedLanguage}`"
          v-model="code"
          :language="editorLanguage"
          :theme="theme"
          :problem-id="problemId"
          :code-skeleton="activeCodeSkeleton"
          :read-only="readOnly"
        />
      </div>

      <!-- 水平分割线 -->
      <div
        class="horizontal-resizer"
        :class="{ 'is-dragging': isDraggingResizer }"
        @mousedown="startResize"
      >
        <div class="resizer-handle"></div>
      </div>

      <!-- 下栏：测试用例标签面板 -->
      <div class="result-content" :style="{ height: bottomHeight + 'px' }">
        <div v-if="runError" class="run-error-banner" role="alert">{{ runError }}</div>
        <TestCasePanel
          :test-cases="mergedTestCases"
          :loading="runLoading"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
interface EditorShortcutEvent {
  key: string
  ctrlKey: boolean
  metaKey: boolean
  shiftKey: boolean
}

export type EditorShortcutAction = 'run' | 'submit'

interface EditorActionAvailability {
  isRunDisabled: boolean
  isSubmitDisabled: boolean
}

interface EditorCodeContext {
  problemId?: string | number
  conversationId?: string
}

const hasBoundProblem = (problemId: string | number | undefined) => {
  return problemId !== undefined && problemId !== null
}

export const isEditorShortcutKey = (event: EditorShortcutEvent) => {
  return event.key === 'Enter' && (event.ctrlKey || event.metaKey)
}

export function shouldResetEditorCodeOnContextChange(
  previous: EditorCodeContext,
  next: EditorCodeContext,
) {
  const previousBound = hasBoundProblem(previous.problemId)
  const nextBound = hasBoundProblem(next.problemId)

  if (previousBound && !nextBound) {
    return true
  }

  if (!nextBound && previous.conversationId !== next.conversationId) {
    return true
  }

  return false
}

export function resolveEditorActionFromKeyboard(
  event: EditorShortcutEvent,
  readOnly: boolean,
  availability: EditorActionAvailability = { isRunDisabled: false, isSubmitDisabled: false },
): EditorShortcutAction | null {
  if (readOnly || event.key !== 'Enter') {
    return null
  }

  const hasCommandModifier = event.ctrlKey || event.metaKey
  if (!hasCommandModifier) {
    return null
  }

  if (event.shiftKey) {
    return availability.isSubmitDisabled ? null : 'submit'
  }

  return availability.isRunDisabled ? null : 'run'
}
</script>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import CodeEditor from './CodeEditor.vue'
import TestCasePanel from './TestCasePanel.vue'
import { ProgrammingLanguage } from '@/types/editor'
import type { AlgorithmCodeTemplate, TestCase, TestCaseResultVO } from '@/types/problem'

const STORAGE_KEY = 'editor-bottom-height'
const DEFAULT_BOTTOM_HEIGHT = 220
const MIN_EDITOR_HEIGHT = 150
const MIN_BOTTOM_HEIGHT = 100
const RESIZER_HEIGHT = 4

interface Props {
  problemId?: string | number
  conversationId?: string
  theme?: 'light' | 'dark'
  codeTemplates?: AlgorithmCodeTemplate[]
  readOnly?: boolean
  sharedTestCases?: TestCase[]
  runResults?: TestCaseResultVO[]
  runLoading?: boolean
  runError?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  theme: 'light',
  readOnly: false,
  codeTemplates: () => [],
  sharedTestCases: () => [],
  runResults: () => [],
  runLoading: false,
  runError: null,
})

const emit = defineEmits<{
  run: [code: string, language: ProgrammingLanguage]
  submit: [code: string, language: ProgrammingLanguage]
}>()

const code = ref('')
const languageLabelMap: Record<ProgrammingLanguage, string> = {
  [ProgrammingLanguage.PYTHON]: 'Python',
  [ProgrammingLanguage.JAVA]: 'Java',
  [ProgrammingLanguage.JAVASCRIPT]: 'JavaScript',
}
const fallbackLanguages: ProgrammingLanguage[] = [
  ProgrammingLanguage.PYTHON,
  ProgrammingLanguage.JAVA,
  ProgrammingLanguage.JAVASCRIPT,
]
const languageOptions = computed<ProgrammingLanguage[]>(() => {
  const mapped = (props.codeTemplates ?? [])
    .map((template) => template.language?.toUpperCase())
    .filter(
      (language): language is ProgrammingLanguage =>
        language === ProgrammingLanguage.PYTHON
        || language === ProgrammingLanguage.JAVA
        || language === ProgrammingLanguage.JAVASCRIPT
    )
  return mapped.length > 0 ? mapped : fallbackLanguages
})
const selectedLanguage = ref<ProgrammingLanguage>(ProgrammingLanguage.PYTHON)
watch(languageOptions, (options) => {
  if (!options.includes(selectedLanguage.value)) {
    selectedLanguage.value = options[0] ?? ProgrammingLanguage.PYTHON
  }
}, { immediate: true })

const activeTemplate = computed(() => {
  return (props.codeTemplates ?? []).find(
    (template) => template.language?.toUpperCase() === selectedLanguage.value
  ) ?? null
})
const activeCodeSkeleton = computed(() => activeTemplate.value?.codeSkeleton ?? undefined)
const editorLanguage = computed(() => {
  switch (selectedLanguage.value) {
    case ProgrammingLanguage.JAVA:
      return 'java'
    case ProgrammingLanguage.JAVASCRIPT:
      return 'javascript'
    default:
      return 'python'
  }
})

const isCodeEmpty = computed(() => !code.value.trim())
const isRunDisabled = computed(() => {
  return isCodeEmpty.value || Boolean(props.readOnly) || Boolean(props.runLoading)
})
const isSubmitDisabled = computed(() => {
  return isCodeEmpty.value || Boolean(props.readOnly)
})

watch(
  [() => props.problemId, () => props.conversationId],
  ([nextProblemId, nextConversationId], [previousProblemId, previousConversationId]) => {
    if (
      shouldResetEditorCodeOnContextChange(
        { problemId: previousProblemId, conversationId: previousConversationId },
        { problemId: nextProblemId, conversationId: nextConversationId },
      )
    ) {
      code.value = ''
    }
  }
)

// Resizable split state
const bodyRef = ref<HTMLElement | null>(null)
const bodyHeight = ref(0)
const isDraggingResizer = ref(false)

function readBottomHeight(): number {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw !== null) {
      const v = parseInt(raw, 10)
      if (!isNaN(v)) return v
    }
  } catch {
    // 隐私模式或 Storage 被禁用
  }
  return DEFAULT_BOTTOM_HEIGHT
}

const bottomHeight = ref<number>(readBottomHeight())

const editorHeight = computed(() => {
  if (!bodyHeight.value) return 400
  return Math.max(MIN_EDITOR_HEIGHT, bodyHeight.value - RESIZER_HEIGHT - bottomHeight.value)
})

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (!bodyRef.value) return
  bodyHeight.value = bodyRef.value.clientHeight
  resizeObserver = new ResizeObserver(() => {
    bodyHeight.value = bodyRef.value?.clientHeight ?? 0
  })
  resizeObserver.observe(bodyRef.value)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

// Merge testCases + runResults by index
const mergedTestCases = computed<TestCase[]>(() =>
  (props.sharedTestCases ?? []).map((tc, i) => {
    const r = props.runResults?.[i]
    if (!r) return tc
    return {
      ...tc,
      result: {
        passed: r.success,
        actualOutput: r.result,
        error: r.errorMessage,
      },
    }
  })
)

let cleanupResize: (() => void) | null = null

const startResize = (e: MouseEvent) => {
  e.preventDefault()
  isDraggingResizer.value = true
  const startY = e.clientY
  const startBottomHeight = bottomHeight.value

  const onMouseMove = (ev: MouseEvent) => {
    if (!bodyRef.value) return
    const totalHeight = bodyRef.value.clientHeight
    const delta = startY - ev.clientY
    const newBottom = Math.min(
      totalHeight - MIN_EDITOR_HEIGHT - RESIZER_HEIGHT,
      Math.max(MIN_BOTTOM_HEIGHT, startBottomHeight + delta)
    )
    bottomHeight.value = Math.round(newBottom)
  }

  const onMouseUp = () => {
    isDraggingResizer.value = false
    try {
      localStorage.setItem(STORAGE_KEY, String(bottomHeight.value))
    } catch {
      // Storage 配额满或被禁用
    }
    cleanupResize = null
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
  cleanupResize = () => {
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }
}

onUnmounted(() => {
  cleanupResize?.()
})

const handleRun = () => {
  if (isRunDisabled.value) {
    return
  }

  emit('run', code.value, selectedLanguage.value)
}

const handleSubmit = () => {
  if (isSubmitDisabled.value) {
    return
  }

  emit('submit', code.value, selectedLanguage.value)
}

const handleEditorShortcut = (event: KeyboardEvent) => {
  if (isEditorShortcutKey(event)) {
    event.preventDefault()
  }

  const action = resolveEditorActionFromKeyboard(event, Boolean(props.readOnly), {
    isRunDisabled: isRunDisabled.value,
    isSubmitDisabled: isSubmitDisabled.value,
  })
  if (!action) {
    return
  }

  if (action === 'submit') {
    handleSubmit()
    return
  }

  handleRun()
}
</script>

<style scoped>
.editor-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-background-soft);
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent), var(--color-background-mute);
  box-shadow: inset 0 -1px 0 rgba(255, 255, 255, 0.02);
  flex-shrink: 0;
}

.editor-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shortcut-hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.language-selector {
  min-height: 34px;
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-background);
  color: var(--color-text);
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}

.language-selector:hover {
  border-color: var(--color-primary);
  background: var(--color-background-soft);
}

.language-selector:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--primary-ring);
}

.btn-run,
.btn-submit {
  min-height: 34px;
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, opacity 0.2s, box-shadow 0.2s, background 0.2s;
}

.btn-run {
  background: var(--color-success);
  color: white;
}

.btn-run:hover:not(:disabled) {
  background: var(--color-success-dark);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.btn-submit {
  background: var(--color-primary);
  color: white;
}

.btn-submit:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.btn-run:disabled,
.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.editor-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.editor-content {
  overflow: hidden;
  flex-shrink: 0;
}

.horizontal-resizer {
  height: 4px;
  background: linear-gradient(90deg, transparent, var(--color-border), transparent);
  cursor: row-resize;
  flex-shrink: 0;
  position: relative;
  transition: background 0.15s;
  user-select: none;
}

.horizontal-resizer:hover,
.horizontal-resizer.is-dragging {
  background: linear-gradient(90deg, transparent, var(--color-primary), transparent);
}

.resizer-handle {
  position: absolute;
  top: -3px;
  left: 0;
  right: 0;
  bottom: -3px;
}

.result-content {
  flex-shrink: 0;
  overflow: hidden;
  border-top: 1px solid var(--color-border);
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}

.run-error-banner {
  margin: 10px 12px 0;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid var(--color-danger);
  background: var(--color-danger-light);
  color: var(--color-danger);
  font-size: 12px;
  line-height: 1.4;
}

@media (max-width: 1100px) {
  .shortcut-hint {
    display: none;
  }
}
</style>
