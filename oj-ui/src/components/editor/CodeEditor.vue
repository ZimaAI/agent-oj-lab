<template>
  <div class="code-editor">
    <div v-if="!editorFailed" ref="editorContainer" class="editor-container"></div>
    <textarea
      v-else
      v-model="fallbackCode"
      class="fallback-textarea"
      :readonly="readOnly"
      placeholder="Enter your code here..."
    ></textarea>
    <div v-if="!isReady && !editorFailed" class="editor-loading">
      <span>Loading editor...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onErrorCaptured, nextTick, computed } from 'vue'
import { useCodeEditor } from '@/composables/useCodeEditor'
import type { CodeEditorLanguage } from '@/composables/useCodeEditor'

interface Props {
  modelValue?: string
  language?: CodeEditorLanguage
  readOnly?: boolean
  theme?: 'light' | 'dark'
  problemId?: string | number
  codeSkeleton?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  language: 'python',
  readOnly: false,
  theme: 'light',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const editorContainer = ref<HTMLElement | null>(null)
const editorFailed = ref(false)
const fallbackCode = ref(props.modelValue)
const codeSkeletonLoaded = ref(false) // Track if we've already loaded the skeleton

// Reactive refs for problemId and codeSkeleton so useCodeEditor always uses current values
const reactiveProblemId = computed(() => props.problemId)
const reactiveCodeSkeleton = computed(() => props.codeSkeleton)

let editorComposable: ReturnType<typeof useCodeEditor> | null = null

try {
  editorComposable = useCodeEditor(
    editorContainer,
    {
      initialCode: props.modelValue,
      language: props.language,
      theme: props.theme,
      readOnly: props.readOnly,
    },
    reactiveProblemId,
    reactiveCodeSkeleton
  )
} catch (error) {
  console.error('Failed to initialize CodeMirror:', error)
  editorFailed.value = true
}

const { code, isReady, setCode, setTheme, setReadOnly, hasSavedCode, loadPersistedCode } = editorComposable || {
  code: ref(props.modelValue),
  isReady: ref(false),
  setCode: () => {},
  setTheme: () => {},
  setReadOnly: () => {},
  hasSavedCode: () => false,
  loadPersistedCode: () => '',
}

// Handle error during editor initialization
onErrorCaptured((error) => {
  console.error('CodeEditor error:', error)
  editorFailed.value = true
  return false
})

// Watch for external code changes
watch(
  () => props.modelValue,
  (newValue) => {
    if (editorFailed.value) {
      fallbackCode.value = newValue
    } else if (isReady.value && newValue !== code.value) {
      setCode(newValue)
    }
  },
  { flush: 'post' } // Run after DOM updates to avoid conflicts
)

// Emit code changes
if (!editorFailed.value) {
  watch(code, (newCode) => {
    emit('update:modelValue', newCode)
  })
}

// Watch fallback textarea changes
watch(fallbackCode, (newCode) => {
  if (editorFailed.value) {
    emit('update:modelValue', newCode)
  }
})

// Watch for theme changes
watch(
  () => props.theme,
  (newTheme) => {
    if (!editorFailed.value) {
      setTheme(newTheme)
    }
  }
)

// Watch for readOnly changes
watch(
  () => props.readOnly,
  (newReadOnly) => {
    if (!editorFailed.value) {
      setReadOnly(newReadOnly ?? false)
    }
  }
)

// Watch for codeSkeleton changes - load it when problem data arrives and editor is ready
watch(
  [() => props.codeSkeleton, isReady],
  async ([newSkeleton, ready]) => {
    if (!editorFailed.value && ready && newSkeleton && !hasSavedCode() && !codeSkeletonLoaded.value) {
      // Wait for next tick to ensure editor state is stable
      await nextTick()

      if (!codeSkeletonLoaded.value && newSkeleton) {
        codeSkeletonLoaded.value = true // Set flag BEFORE calling setCode to prevent re-entry
        setCode(newSkeleton)
      }
    }
  },
  { flush: 'post' } // Run after DOM updates to avoid conflicts
)

// Watch for problemId/codeSkeleton changes - reset the skeleton loaded flag when switching problems
// and immediately apply the new skeleton if the editor is ready and there's no saved code
watch(
  [() => props.problemId, () => props.codeSkeleton],
  async ([newId, newSkeleton], [oldId, oldSkeleton]) => {
    if (newId !== oldId || newSkeleton !== oldSkeleton) {
      codeSkeletonLoaded.value = false
      if (!editorFailed.value && isReady.value) {
        await nextTick()
        if (!newId && !newSkeleton) {
          // No problem bound to this conversation - clear the editor
          // Keep codeSkeletonLoaded = false so a subsequent problem load can apply its skeleton
          setCode('')
        } else if (newSkeleton) {
          if (hasSavedCode()) {
            // Restore previously saved code for this problem
            setCode(loadPersistedCode())
          } else if (!codeSkeletonLoaded.value) {
            // New problem with skeleton and no saved code - apply skeleton
            codeSkeletonLoaded.value = true
            setCode(newSkeleton)
          }
        }
      }
    }
  }
)
</script>

<style scoped>
.code-editor {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.editor-container {
  width: 100%;
  height: 100%;
}

.editor-loading {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-background);
  color: var(--color-text);
}

:deep(.cm-editor) {
  height: 100%;
  font-size: 14px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

:deep(.cm-scroller) {
  overflow: auto;
}

:deep(.cm-content) {
  padding: 8px 0;
}

:deep(.cm-line) {
  padding: 0 8px;
}

:deep(.cm-gutters) {
  border-right: 1px solid var(--color-border);
  background: var(--color-background-soft);
}

:deep(.cm-activeLineGutter) {
  background: var(--color-primary-dim);
  color: var(--color-primary);
}

:deep(.cm-activeLine) {
  background: var(--color-primary-dim);
}

:deep(.cm-tooltip.cm-tooltip-autocomplete) {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-md);
  background: var(--color-background-soft);
  color: var(--color-text);
}

:deep(.cm-completionLabel) {
  font-size: 13px;
}

:deep(.cm-completionDetail) {
  color: var(--color-text-secondary);
}

.fallback-textarea {
  width: 100%;
  height: 100%;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-background);
  color: var(--color-text);
  font-size: 14px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  line-height: 1.5;
  resize: none;
}

.fallback-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
}

.fallback-textarea:read-only {
  background: var(--color-background-soft);
  cursor: not-allowed;
}
</style>
