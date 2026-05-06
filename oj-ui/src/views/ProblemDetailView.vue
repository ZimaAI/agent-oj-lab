<template>
  <div class="problem-detail-view">
    <HistorySidebar
      :collapsed="sidebarCollapsed"
      :width="sidebarWidth"
      @toggle="toggleSidebar"
    />

    <div class="problem-panel" :style="{ width: `${problemWidth}px` }">
      <div v-if="loading || loadingConversation" class="loading-state">
        <div class="spinner"></div>
        <p>{{ loadingConversation ? '加载会话中...' : '加载中...' }}</p>
      </div>
      <div v-else-if="conversationError" class="error-state">
        <span class="state-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" />
            <path d="M12 8V13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <circle cx="12" cy="16" r="1" fill="currentColor" />
          </svg>
        </span>
        <p>{{ conversationError }}</p>
        <button @click="entryMode === 'from-history' ? initializeFromHistory() : initializeFromList()" class="retry-button">重试</button>
      </div>
      <div v-else-if="notFound" class="error-state not-found">
        <span class="state-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="1.8" />
            <path d="M16 16L20 20" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </span>
        <h3>题目不存在</h3>
        <p>该题目可能已被删除或ID不正确</p>
        <button @click="$router.push('/problems')" class="retry-button">返回题库</button>
      </div>
      <div v-else-if="error" class="error-state">
        <span class="state-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" />
            <path d="M12 8V13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <circle cx="12" cy="16" r="1" fill="currentColor" />
          </svg>
        </span>
        <p>{{ error }}</p>
        <button @click="loadProblem(Number(problemId))" class="retry-button">重试</button>
      </div>
      <div v-else-if="!problem && currentConversationId" class="empty-state">
        <span class="state-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M5 6.5C5 5.67 5.67 5 6.5 5H17.5C18.33 5 19 5.67 19 6.5V14.5C19 15.33 18.33 16 17.5 16H10.5L6 19V16H6.5C5.67 16 5 15.33 5 14.5V6.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
          </svg>
        </span>
        <p>开始对话，AI 助手会为你推荐题目</p>
      </div>
      <ProblemPanel
        v-else-if="problem"
        :problem="problem"
        :current-question-id="currentQuestionId"
        :evaluation-result="evaluationResult"
        :active-tab="problemPanelActiveTab"
        @update:active-tab="(value) => (problemPanelActiveTab = value)"
      />
      <div v-if="showIntentOverlay" class="intent-overlay">
        <div class="intent-overlay-content">
          <span class="intent-overlay-icon">↻</span>
          <p v-if="intentOverlayState.questionLoading || intentOverlayState.evaluationLoading" class="intent-overlay-text">
            正在处理请求...
          </p>
        </div>
      </div>
    </div>

    <PanelResizer
      :is-dragging="isDragging && dragTarget === 'problem'"
      @drag-start="(e) => startDrag('problem', e)"
      @keyboard-resize="(delta) => resizeByKeyboard('problem', delta)"
    />

    <div
      class="chat-panel"
      :style="{ width: chatCollapsed ? '0' : `${chatWidth}px` }"
      :aria-hidden="chatCollapsed"
      :inert="chatCollapsed"
    >
      <ChatPanel
        ref="chatPanelRef"
        :conversation-id="currentConversationId"
        :external-overlay-text="chatNodeHintText"
        :external-overlay-node-name="chatNodeHintNodeName"
        @collapse="toggleChat"
        @intent="handleIntentEvent"
        @overlayText="handleOverlayText"
        @structuredResult="handleStructuredResult"
        @streamComplete="handleStreamComplete"
        @streamError="handleStreamError"
      />
    </div>

    <PanelResizer
      v-if="!chatCollapsed"
      :is-dragging="isDragging && dragTarget === 'chat'"
      @drag-start="(e) => startDrag('chat', e)"
      @keyboard-resize="(delta) => resizeByKeyboard('chat', delta)"
    />

    <div class="editor-panel" :style="{ flex: 1 }">
      <Suspense>
        <EditorPanel
          :theme="theme"
          :conversation-id="currentConversationId"
          :problem-id="problem?.id"
          :code-templates="problem?.codeTemplates ?? []"
          :read-only="!problem"
          :shared-test-cases="problem?.sharedTestCases ?? []"
          :run-results="runResults"
          :run-loading="runLoading"
          :run-error="runError ?? undefined"
          @run="handleRun"
          @submit="handleSubmit"
        />
        <template #fallback>
          <div class="loading-state">
            <div class="spinner"></div>
            <p>加载编辑器...</p>
          </div>
        </template>
      </Suspense>
      <div v-if="showIntentOverlay" class="intent-overlay">
        <div class="intent-overlay-content">
          <span class="intent-overlay-icon">↻</span>
          <p v-if="intentOverlayState.questionLoading || intentOverlayState.evaluationLoading" class="intent-overlay-text">
            正在处理请求...
          </p>
        </div>
      </div>
    </div>

    <AuthModal :show="showAuthModal" @close="showAuthModal = false" message="提交代码需要登录，请先登录" />
    <ChatToggleButton :is-open="!chatCollapsed" @toggle="toggleChat" />
  </div>
</template>

<script lang="ts">
import type { WorkflowStructuredResultPayload } from '@/types/chat'
import type { CodeEvaluation, TestResult } from '@/types/evaluation'

interface BackendExecutionResultItem {
  passed?: boolean
  output?: unknown
  error?: unknown
}

interface BackendExecutionResultPayload {
  results?: BackendExecutionResultItem[]
}

interface BackendCodeEvaluationLike {
  correctnessScore?: unknown
  timeComplexityScore?: unknown
  spaceComplexityScore?: unknown
  timeComplexityAnalysis?: unknown
  spaceComplexityAnalysis?: unknown
  suggestions?: unknown
  testResults?: unknown
}

interface BackendCodeEvaluationOutputWrapper {
  codeEvaluationNodeOutput?: {
    success?: boolean
    codeEvaluation?: unknown
  }
  success?: boolean
  codeEvaluation?: unknown
}

function toNumberOrZero(value: unknown): number {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : 0
}

function toStringOrEmpty(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
}

function toStringOrNull(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null
  }
  return String(value)
}

function normalizeEvaluationTestResultsArray(rawResults: unknown[]): TestResult[] {
  return rawResults.map((item, index) => {
    const current = (item ?? {}) as Record<string, unknown>

    if (typeof current.success === 'boolean' || 'result' in current || 'errorMessage' in current) {
      const error = toStringOrNull(current.errorMessage)
      return {
        testCase: `测试用例 ${index + 1}`,
        passed: Boolean(current.success),
        output: toStringOrEmpty(current.result),
        error: error ?? '',
      }
    }

    if (typeof current.passed === 'boolean' || 'output' in current || 'error' in current) {
      const error = toStringOrNull(current.error)
      return {
        testCase: `测试用例 ${index + 1}`,
        passed: Boolean(current.passed),
        output: toStringOrEmpty(current.output),
        error: error ?? '',
      }
    }

    return {
      testCase: toStringOrEmpty(current.testCase) || `测试用例 ${index + 1}`,
      passed: Boolean(current.passed),
      output: toStringOrEmpty(current.output),
      error: toStringOrEmpty(current.error),
    }
  })
}

function normalizeEvaluationTestResults(testResults: unknown): TestResult[] {
  if (Array.isArray(testResults)) {
    return normalizeEvaluationTestResultsArray(testResults)
  }

  if (typeof testResults !== 'string' || !testResults.trim()) {
    return []
  }

  try {
    const parsed = JSON.parse(testResults) as BackendExecutionResultPayload | unknown[]
    if (Array.isArray(parsed)) {
      return normalizeEvaluationTestResultsArray(parsed)
    }
    return normalizeEvaluationTestResultsArray(Array.isArray(parsed?.results) ? parsed.results : [])
  } catch {
    return []
  }
}

function unwrapCodeEvaluationPayload(result: unknown): BackendCodeEvaluationOutputWrapper {
  if (!result || typeof result !== 'object') {
    return {}
  }

  const current = result as BackendCodeEvaluationOutputWrapper
  if (current.codeEvaluationNodeOutput && typeof current.codeEvaluationNodeOutput === 'object') {
    return current.codeEvaluationNodeOutput
  }
  return current
}

function hasEvaluationFields(candidate: BackendCodeEvaluationLike): boolean {
  return [
    'correctnessScore',
    'timeComplexityScore',
    'spaceComplexityScore',
    'timeComplexityAnalysis',
    'spaceComplexityAnalysis',
    'suggestions',
    'testResults',
  ].some((field) => (candidate as Record<string, unknown>)[field] !== undefined)
}

function hasQuestionFields(candidate: Record<string, unknown>): boolean {
  return [
    'title',
    'description',
    'standardCasePool',
    'sharedFunctionName',
    'sharedCodeSkeleton',
    'sharedTestCases',
    'codeTemplates',
  ].some((field) => candidate[field] !== undefined)
}

function unwrapAssembledQuestion(result: unknown): unknown | null {
  if (!result || typeof result !== 'object') {
    return null
  }

  const resultData = result as Record<string, unknown>
  const assembledOutput = resultData.multiLanguageCodeAssembleNodeOutput
  if (assembledOutput && typeof assembledOutput === 'object') {
    const assembledData = assembledOutput as Record<string, unknown>
    if (assembledData.question && typeof assembledData.question === 'object') {
      return assembledData.question
    }
    return hasQuestionFields(assembledData) ? assembledData : null
  }

  return hasQuestionFields(resultData) ? resultData : null
}

export function resolveQuestionDataFromStructuredResultPayload(
  payload: WorkflowStructuredResultPayload
): unknown | null {
  if (payload.nodeName === 'MultiLanguageCodeAssembleNode') {
    return unwrapAssembledQuestion(payload.result)
  }

  if (payload.nodeName !== 'RAGJudgeNode' || !payload.result || typeof payload.result !== 'object') {
    return null
  }

  const resultData = payload.result as Record<string, unknown>
  const isMatched = (resultData.is_matched ?? resultData.isMatched) === true
  if (!isMatched) {
    return null
  }

  return resultData.selected_question ?? resultData.selectedQuestion ?? null
}

export function resolveProblemPanelTabFromStructuredResultPayload(
  payload: WorkflowStructuredResultPayload
): 'description' | 'submissions' | 'evaluation' | null {
  if (resolveQuestionDataFromStructuredResultPayload(payload)) {
    return 'description'
  }
  if (payload.nodeName === 'CodeEvaluationNode' || payload.nodeName === 'CodeExecutionNode') {
    return 'evaluation'
  }
  return null
}

export function resolveEvaluationFromStructuredResultPayload(
  payload: WorkflowStructuredResultPayload
): CodeEvaluation | null {
  if (payload.nodeName !== 'CodeEvaluationNode' && payload.nodeName !== 'CodeExecutionNode') {
    return null
  }

  const unwrapped = unwrapCodeEvaluationPayload(payload.result)
  const directEvaluationCandidate = unwrapped as BackendCodeEvaluationLike
  const wrappedEvaluationCandidate =
    unwrapped.codeEvaluation && typeof unwrapped.codeEvaluation === 'object'
      ? (unwrapped.codeEvaluation as BackendCodeEvaluationLike)
      : null

  const rawEvaluation =
    wrappedEvaluationCandidate ??
    (hasEvaluationFields(directEvaluationCandidate) ? directEvaluationCandidate : null)

  if (!rawEvaluation) {
    return null
  }

  return {
    correctnessScore: toNumberOrZero(rawEvaluation.correctnessScore),
    timeComplexityScore: toNumberOrZero(rawEvaluation.timeComplexityScore),
    spaceComplexityScore: toNumberOrZero(rawEvaluation.spaceComplexityScore),
    testResults: normalizeEvaluationTestResults(rawEvaluation.testResults),
    timeComplexityAnalysis: toStringOrEmpty(rawEvaluation.timeComplexityAnalysis),
    spaceComplexityAnalysis: toStringOrEmpty(rawEvaluation.spaceComplexityAnalysis),
    suggestions: toStringOrNull(rawEvaluation.suggestions),
  }
}
</script>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useResizablePanels } from '@/composables/useResizablePanels'
import { useProblemDetail } from '@/composables/useProblemDetail'
import { useAuthStore } from '@/stores/auth'
import { defineAsyncComponent } from 'vue'
import { createConversation, getConversationSummaryById } from '@/api/conversation'
import { problemApi } from '@/api/problem'
import { submitCodeStream } from '@/api/codeSubmission'
import type {
  ConversationMessage,
  StreamIntentType,
  WorkflowOverlayPayload,
} from '@/types/chat'
import {
  createIntentOverlayState,
  handleSubmissionStreamFailure,
  nextIntentOverlayState,
} from '@/views/problem-detail/intentOverlayState'
import { resolvePersistedQuestionId } from '@/views/problem-detail/persistedQuestionResolver'
import { emitConversationSummaryUpdate } from '@/utils/conversationSummarySync'
import HistorySidebar from '@/components/layout/HistorySidebar.vue'
import PanelResizer from '@/components/layout/PanelResizer.vue'
import ProblemPanel from '@/components/problem/ProblemPanel.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import ChatToggleButton from '@/components/chat/ChatToggleButton.vue'
import AuthModal from '@/components/AuthModal.vue'
import type { ProgrammingLanguage } from '@/types/editor'
import type { AlgorithmQuestion } from '@/types/problem'

// Lazy load EditorPanel to reduce initial bundle size (~500KB CodeMirror)
const EditorPanel = defineAsyncComponent(() => import('@/components/editor/EditorPanel.vue'))

const route = useRoute()
const router = useRouter()
const problemId = computed(() => route.params.id as string)
const conversationId = computed(() => route.params.conversationId ? String(route.params.conversationId) : undefined)
const theme = 'light' as const
const authStore = useAuthStore()
const showAuthModal = ref(false)

const {
  sidebarWidth,
  problemWidth,
  chatWidth,
  editorWidth,
  sidebarCollapsed,
  chatCollapsed,
  isDragging,
  dragTarget,
  toggleSidebar,
  toggleChat,
  startDrag,
  resizeByKeyboard,
} = useResizablePanels()

const { problem, loading, error, notFound, loadProblem, clearProblem, runCode, runResults, runLoading, runError } = useProblemDetail()
const currentConversationId = ref<string | undefined>(conversationId.value)
const currentQuestionId = ref<number | undefined>(undefined)
const loadingConversation = ref(false)
const conversationError = ref<string | null>(null)
const evaluationResult = ref<CodeEvaluation | null>(null)
const problemPanelActiveTab = ref<'description' | 'submissions' | 'evaluation'>('description')
const abortController = ref<AbortController | null>(null)
const chatPanelRef = ref<InstanceType<typeof ChatPanel> | null>(null)
const intentOverlayState = ref(createIntentOverlayState())
const chatNodeHintText = ref('')
const chatNodeHintNodeName = ref<string | null>(null)
const unresolvedQuestionError = '题目正在保存，请稍后重试'

// Entry mode: "from-history" (resume existing), "from-list" (create new conversation), or "browse" (no params, just show sidebar)
const entryMode = computed(() => {
  if (conversationId.value) return 'from-history'
  if (problemId.value) return 'from-list'
  return 'browse'
})

// Initialize conversation and problem
const initializeFromList = async () => {
  if (!problemId.value) return

  try {
    loadingConversation.value = true
    conversationError.value = null

    // 并行：创建会话（后端根据 currentQuestionId 查库设置标题）和加载题目
    const [conversation] = await Promise.all([
      createConversation({ currentQuestionId: Number(problemId.value) }),
      loadProblem(Number(problemId.value)),
    ])
    currentConversationId.value = conversation.conversationId
    currentQuestionId.value = conversation.currentQuestionId ?? Number(problemId.value)

    // Update URL to include conversationId without navigation
    router.replace({
      name: route.name || undefined,
      params: { ...route.params, conversationId: conversation.conversationId }
    })
  } catch (err: any) {
    conversationError.value = err.message || '创建会话失败'
    console.error('Failed to initialize from list:', err)
  } finally {
    loadingConversation.value = false
  }
}

const initializeFromHistory = async () => {
  if (!conversationId.value) return

  try {
    loadingConversation.value = true
    conversationError.value = null
    clearProblem()

    // 通过已验证的会话列表接口查找会话摘要
    const conversation = await getConversationSummaryById(conversationId.value)
    if (!conversation) {
      throw new Error('会话不存在或暂不可见')
    }

    currentConversationId.value = conversation.conversationId
    currentQuestionId.value = conversation.currentQuestionId ?? undefined

    // 加载当前会话关联的题目
    if (conversation.currentQuestionId) {
      await loadProblem(conversation.currentQuestionId)
    }
  } catch (err: any) {
    conversationError.value = err.message || '加载会话失败'
    console.error('Failed to initialize from history:', err)
  } finally {
    loadingConversation.value = false
  }
}

// 同步生成题目的页面状态与会话摘要
const syncGeneratedQuestionState = (questionData: unknown) => {
  if (!questionData) {
    return
  }

  const nextProblem = mapGeneratedQuestion(questionData)
  problem.value = nextProblem
  currentQuestionId.value = nextProblem.id > 0 ? nextProblem.id : undefined
  void ensureCurrentQuestionId({ requireConversationSync: true })

  if (currentConversationId.value && nextProblem.title) {
    emitConversationSummaryUpdate({
      id: currentConversationId.value,
      title: nextProblem.title,
      currentQuestionId: nextProblem.id > 0 ? nextProblem.id : currentQuestionId.value,
      lastMessageTime: new Date().toISOString(),
    })
  }
}

const updateIntentOverlay = (event: Parameters<typeof nextIntentOverlayState>[1]) => {
  intentOverlayState.value = nextIntentOverlayState(intentOverlayState.value, event)
}

const showIntentOverlay = computed(() => {
  return intentOverlayState.value.questionLoading || intentOverlayState.value.evaluationLoading
})

const handleIntentEvent = (intent: StreamIntentType) => {
  updateIntentOverlay({ type: 'intent', data: intent })
}

const handleStreamComplete = () => {
  updateIntentOverlay({ type: 'complete' })
  chatNodeHintText.value = ''
  chatNodeHintNodeName.value = null
}

const handleStreamError = (_message: string) => {
  updateIntentOverlay({ type: 'error' })
  chatNodeHintText.value = ''
  chatNodeHintNodeName.value = null
}

// 处理浮层文本事件
const handleOverlayText = (payload: WorkflowOverlayPayload) => {
  chatNodeHintText.value = payload.text
  chatNodeHintNodeName.value = payload.nodeName
  updateIntentOverlay({
    type: 'overlayText',
    data: {
      text: payload.text,
      nodeName: payload.nodeName,
    },
  })
}

// 处理结构化结果事件
const handleStructuredResult = (payload: WorkflowStructuredResultPayload) => {
  chatNodeHintText.value = ''
  chatNodeHintNodeName.value = null
  updateIntentOverlay({ type: 'structuredResult', data: { nodeName: payload.nodeName } })

  const nextQuestionData = resolveQuestionDataFromStructuredResultPayload(payload)
  const targetTab = resolveProblemPanelTabFromStructuredResultPayload(payload)

  if (nextQuestionData) {
    syncGeneratedQuestionState(nextQuestionData)
    intentOverlayState.value = createIntentOverlayState()
  }

  const nextEvaluation = resolveEvaluationFromStructuredResultPayload(payload)
  if (nextEvaluation) {
    // 更新评审结果面板
    evaluationResult.value = nextEvaluation
  }

  if (targetTab) {
    problemPanelActiveTab.value = targetTab
  }
}

const ensureCurrentQuestionId = async (
  options: { requireConversationSync?: boolean } = {}
) => {
  const resolvedQuestionId = await resolvePersistedQuestionId({
    streamedQuestionId: typeof problem.value?.id === 'number' ? problem.value.id : undefined,
    currentQuestionId: currentQuestionId.value,
    conversationId: currentConversationId.value,
    requireConversationSync: options.requireConversationSync ?? false,
    loadConversationSummary: getConversationSummaryById,
  })

  if (resolvedQuestionId) {
    currentQuestionId.value = resolvedQuestionId
  }

  return resolvedQuestionId
}

const normalizeQuestionTestCases = (testCases: unknown) => {
  if (Array.isArray(testCases)) {
    return testCases
  }

  if (typeof testCases === 'string' && testCases.trim()) {
    try {
      const parsed = JSON.parse(testCases)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }

  return []
}

const normalizeStandardCasePool = (casePool: unknown) => {
  if (Array.isArray(casePool)) {
    return casePool
      .map((item) => {
        const current = (item ?? {}) as Record<string, unknown>
        const stdin = typeof current.stdin === 'string' ? current.stdin : null
        const expectedStdout = typeof current.expectedStdout === 'string' ? current.expectedStdout : null
        const publicCase = typeof current.publicCase === 'boolean'
          ? current.publicCase
          : (typeof current.isPublic === 'boolean' ? current.isPublic : true)
        if (!stdin || !expectedStdout) {
          return null
        }
        return {
          stdin,
          expectedStdout,
          publicCase,
          description: typeof current.description === 'string' ? current.description : null,
        }
      })
      .filter((item): item is { stdin: string; expectedStdout: string; publicCase: boolean; description: string | null } => item !== null)
  }

  if (typeof casePool === 'string' && casePool.trim()) {
    try {
      const parsed = JSON.parse(casePool)
      return normalizeStandardCasePool(parsed)
    } catch {
      return []
    }
  }

  return []
}

const normalizeCodeTemplates = (templates: unknown) => {
  if (!Array.isArray(templates)) {
    return []
  }
  return templates
    .map((item) => {
      const current = (item ?? {}) as Record<string, unknown>
      const language = typeof current.language === 'string' ? current.language : ''
      if (!language) {
        return null
      }
      return {
        language,
        functionName: typeof current.functionName === 'string' ? current.functionName : null,
        codeSkeleton: typeof current.codeSkeleton === 'string' ? current.codeSkeleton : null,
      }
    })
    .filter((item): item is { language: string; functionName: string | null; codeSkeleton: string | null } => item !== null)
}

const mapGeneratedQuestion = (questionData: any): AlgorithmQuestion => {
  const standardCasePool = normalizeStandardCasePool(questionData.standardCasePool)
  const sharedTestCases = standardCasePool.length > 0
    ? standardCasePool.map((item) => ({
        input: item.stdin,
        expectedOutput: item.expectedStdout,
        description: item.description,
      }))
    : normalizeQuestionTestCases(questionData.sharedTestCases ?? questionData.testCases)

  return {
    id: questionData.id ?? 0,
    title: questionData.title ?? '',
    description: questionData.description ?? '',
    difficulty: questionData.difficulty ?? null,
    type: questionData.type === 'AI' || questionData.type === 'SYSTEM' ? questionData.type : null,
    standardCasePool,
    sharedFunctionName: questionData.sharedFunctionName ?? questionData.functionName ?? null,
    sharedCodeSkeleton: questionData.sharedCodeSkeleton ?? questionData.codeSkeleton ?? null,
    sharedTestCases,
    codeTemplates: normalizeCodeTemplates(questionData.codeTemplates),
    // Backward-compatible aliases for local transitional rendering.
    codeSkeleton: questionData.sharedCodeSkeleton ?? questionData.codeSkeleton ?? null,
    testCases: normalizeQuestionTestCases(questionData.sharedTestCases ?? questionData.testCases),
    conversationId: questionData.conversationId ?? null,
    traceId: questionData.traceId ?? null,
    agentName: questionData.agentName ?? null,
    createTime: questionData.createTime ?? '',
    updateTime: questionData.updateTime ?? '',
    tags: Array.isArray(questionData.tags)
      ? questionData.tags.map((t: any) => ({
          id: typeof t?.id === 'number' ? t.id : 0,
          tagName: typeof t === 'string' ? t : (t?.tagName ?? ''),
        }))
      : [],
  }
}

const handleRun = async (code: string, language: ProgrammingLanguage) => {
  const questionId = await ensureCurrentQuestionId()
  if (!questionId) {
    runError.value = unresolvedQuestionError
    return
  }
  runCode(code, language, questionId)
}

const handleSubmit = async (code: string, language: ProgrammingLanguage) => {
  if (!authStore.isAuthenticated) {
    showAuthModal.value = true
    return
  }

  if (!currentConversationId.value) {
    console.error('Missing problem ID or conversation ID')
    return
  }

  if (!chatPanelRef.value) {
    console.error('ChatPanel ref not available')
    return
  }

  // Cancel previous request if exists and clear the previous optimistic stream placeholder.
  if (abortController.value) {
    abortController.value.abort()
    chatPanelRef.value.errorStreaming('提交已取消')
  }

  // Store message IDs for cleanup
  const userMessageId = Date.now()
  const agentMessageId = userMessageId + 1

  try {
    abortController.value = new AbortController()
    const questionId = await ensureCurrentQuestionId({ requireConversationSync: true })
    if (!questionId) {
      runError.value = unresolvedQuestionError
      abortController.value = null
      return
    }

    // Clear previous evaluation result
    evaluationResult.value = null

    updateIntentOverlay({ type: 'submissionStart' })

    // Add user message to ChatPanel (CODE_SUBMISSION type)
    const userMessage: ConversationMessage = {
      id: userMessageId,
      conversationId: currentConversationId.value,
      sender: 'USER',
      messageType: 'CODE_SUBMISSION',
      content: `已提交\n\`\`\`${language}\n${code}\n\`\`\``,
      resultType: undefined,
      resultData: undefined,
      createTime: new Date().toISOString()
    }
    chatPanelRef.value.addMessage(userMessage)

    // Add agent message placeholder and start streaming
    const agentMessage: ConversationMessage = {
      id: agentMessageId,
      conversationId: currentConversationId.value,
      sender: 'AGENT',
      content: '',
      resultType: undefined,
      resultData: undefined,
      createTime: new Date().toISOString()
    }
    chatPanelRef.value.startStreaming(agentMessage)

    // Call streaming API
    await submitCodeStream(
      {
        code,
        algorithmQuestionId: questionId,
        conversationId: currentConversationId.value,
        language
      },
      {
        onAssistantToken: (token: string) => {
          chatPanelRef.value?.updateStreamingToken(token)
        },
        onOverlayText: (payload) => {
          handleOverlayText(payload)
        },
        onStructuredResult: (payload) => {
          handleStructuredResult(payload)
        },
        onIntent: (intent: StreamIntentType) => {
          handleIntentEvent(intent)
        },
        onComplete: () => {
          updateIntentOverlay({ type: 'complete' })
          chatNodeHintText.value = ''
          chatNodeHintNodeName.value = null
          chatPanelRef.value?.completeStreaming()
          abortController.value = null
        },
        onError: (errorMsg: string) => {
          console.error('Code submission failed:', errorMsg)
          updateIntentOverlay({ type: 'error' })
          chatNodeHintText.value = ''
          chatNodeHintNodeName.value = null
          chatPanelRef.value?.errorStreaming(errorMsg)
          runError.value = errorMsg || '提交失败'
          abortController.value = null
        }
      },
      abortController.value.signal
    )
  } catch (e: unknown) {
    const handled = handleSubmissionStreamFailure(e, {
      clearOverlay: () => {
        intentOverlayState.value = createIntentOverlayState()
        chatNodeHintText.value = ''
        chatNodeHintNodeName.value = null
      },
      markStreamError: (message: string) => {
        console.error('Submit failed:', e)
        runError.value = message
        updateIntentOverlay({ type: 'error' })
        chatPanelRef.value?.errorStreaming(message)
      },
    })

    if (handled) {
      abortController.value = null
      return
    }

    abortController.value = null
  }
}

// Watch for route changes
watch(() => route.params.conversationId, (newConversationId) => {
  if (newConversationId) {
    intentOverlayState.value = createIntentOverlayState()
    chatNodeHintText.value = ''
    chatNodeHintNodeName.value = null
    currentConversationId.value = String(newConversationId)
    initializeFromHistory()
  }
})

onMounted(() => {
  if (entryMode.value === 'from-history') {
    initializeFromHistory()
  } else if (entryMode.value === 'from-list') {
    initializeFromList()
  }
  // 'browse' mode: just show sidebar, no conversation initialization
})

onUnmounted(() => {
  intentOverlayState.value = createIntentOverlayState()
  chatNodeHintText.value = ''
  chatNodeHintNodeName.value = null
  if (abortController.value) {
    abortController.value.abort()
  }
})
</script>

<style scoped>
.problem-detail-view {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.problem-panel,
.chat-panel,
.editor-panel {
  height: 100%;
  overflow: hidden;
  background-color: var(--color-background);
}

.problem-panel {
  border-right: 1px solid var(--color-border);
  min-width: 300px;
  position: relative;
}

.chat-panel {
  border-right: 1px solid var(--color-border);
  transition: width 0.3s ease;
}

.chat-panel[aria-hidden='true'] {
  pointer-events: none;
}

.editor-panel {
  min-width: 300px;
  position: relative;
}

.intent-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(10, 16, 28, 0.58);
  z-index: 10;
  pointer-events: none;
}

.intent-overlay-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  max-width: 80%;
  text-align: center;
}

.intent-overlay-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 30px;
  animation: spin 0.9s linear infinite;
}

.intent-overlay-text {
  margin: 0;
  color: #fff;
  font-size: 14px;
  line-height: 1.5;
}

.loading-state,
.error-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
}

.state-icon {
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  color: var(--color-text-secondary);
}

.state-icon svg {
  width: 100%;
  height: 100%;
}

.error-state.not-found h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}

.error-state .state-icon {
  color: var(--color-danger);
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-state p,
.error-state p {
  font-size: 14px;
  margin-bottom: 12px;
}

.retry-button {
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.retry-button:hover {
  background: var(--color-primary-dark);
  border-color: var(--color-primary-dark);
}

.retry-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}
</style>
