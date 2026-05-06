<template>
  <main class="page">
    <header class="page-header">
      <h1 class="page-title">算法题管理</h1>
      <p class="page-subtitle">按关键字与难度筛选题目，支持批量导出、批量删除与文档联动管理。</p>
    </header>

    <section class="panel">
      <div class="toolbar">
        <input
          v-model.trim="keyword"
          class="input"
          type="text"
          aria-label="Filter questions by keyword"
          placeholder="Search by keyword"
          @keyup.enter="onSearch"
        >
        <select v-model="difficulty" class="select" aria-label="Filter questions by difficulty">
          <option value="">All Difficulties</option>
          <option v-for="level in difficultyOptions" :key="level" :value="level">{{ level }}</option>
        </select>
        <select v-model="questionType" class="select" aria-label="Filter questions by type">
          <option value="">All Types</option>
          <option v-for="type in questionTypeOptions" :key="type" :value="type">{{ type }}</option>
        </select>
        <button class="btn btn-primary-action" :disabled="loading" @click="onSearch">Search</button>
        <button class="btn btn-light" :disabled="loading" @click="onReset">Reset</button>
        <button class="btn btn-light" :disabled="loading" @click="loadQuestions">Refresh</button>
        <button class="btn btn-light btn-create-action" :disabled="loading" @click="openCreate">Create</button>
      </div>

      <div v-if="!errorMessage" class="selection-toolbar">
        <div class="selection-summary">
          <strong>Selection</strong>
          <span class="selection-count">{{ selectedIds.length }}</span>
          <span class="muted">/ {{ questions.length }} on this page</span>
        </div>
        <div class="selection-actions">
          <button
            class="btn btn-light"
            :disabled="loading || batchDeleting || questions.length === 0"
            @click="selectCurrentPage"
          >
            Select Current Page
          </button>
          <button
            class="btn btn-light"
            :disabled="loading || batchDeleting || questions.length === 0"
            @click="invertCurrentPageSelection"
          >
            Invert Current Page
          </button>
          <button
            class="btn btn-light"
            :disabled="loading || batchDeleting || selectedIds.length === 0"
            @click="clearSelection"
          >
            Clear Selection
          </button>
          <button
            class="btn btn-light"
            :disabled="loading || batchDeleting || selectedIds.length === 0"
            @click="onBatchExport"
          >
            Export Selected CSV
          </button>
          <button
            class="btn btn-danger"
            :disabled="batchDeleting || selectedIds.length === 0"
            @click="onBatchDelete"
          >
            {{ batchDeleteButtonText }}
          </button>
        </div>
      </div>

      <p v-if="noticeMessage" class="notice" role="status" aria-live="polite" aria-atomic="true">{{ noticeMessage }}</p>

      <div v-if="loading" class="table-shell table-shell-loading" aria-busy="true">
        <p class="table-loading-hint">Loading question list, please wait...</p>
        <table class="table table-main">
          <thead>
            <tr>
              <th class="checkbox-cell">
                <span class="skeleton-line skeleton-check" />
              </th>
              <th class="numeric">ID</th>
              <th>Title</th>
              <th>Difficulty</th>
              <th>Type</th>
              <th>Tags</th>
              <th class="date-cell">Updated At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="index in skeletonRowCount" :key="`skeleton-${index}`">
              <td class="checkbox-cell"><span class="skeleton-line skeleton-check" /></td>
              <td class="numeric"><span class="skeleton-line skeleton-id" /></td>
              <td><span class="skeleton-line" /></td>
              <td><span class="skeleton-line skeleton-tag" /></td>
              <td><span class="skeleton-line skeleton-tag" /></td>
              <td><span class="skeleton-line" /></td>
              <td class="date-cell"><span class="skeleton-line skeleton-date" /></td>
              <td><span class="skeleton-line skeleton-action" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="errorMessage" class="state state-error">
        <span>{{ errorMessage }}</span>
        <button class="btn btn-light" @click="loadQuestions">Retry</button>
      </div>
      <div v-else-if="questions.length === 0" class="state state-empty">
        <p>No questions found for current filters.</p>
        <p>Try adjusting keyword / filters, or create a new question.</p>
        <button class="btn btn-light" @click="openCreate">Create Question</button>
      </div>
      <div v-else>
        <div class="table-shell">
          <table class="table table-main">
            <thead>
              <tr>
                <th class="checkbox-cell">
                  <input
                    type="checkbox"
                    :checked="allSelectedOnPage"
                    :indeterminate.prop="partiallySelectedOnPage"
                    :disabled="batchDeleting"
                    aria-label="Select all questions on current page"
                    @change="onToggleAll($event)"
                  >
                </th>
                <th class="numeric">ID</th>
                <th>Title</th>
                <th>Difficulty</th>
                <th>Type</th>
                <th>Tags</th>
                <th class="date-cell">Updated At</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in questions"
                :key="item.id"
                :class="{ 'row-selected': isRowSelected(item.id) }"
              >
                <td class="checkbox-cell">
                  <input
                    type="checkbox"
                    :checked="isRowSelected(item.id)"
                    :disabled="batchDeleting"
                    :aria-label="`Select question ${item.id}`"
                    @change="onToggleSingle(item.id, $event)"
                  >
                </td>
                <td class="numeric">{{ item.id }}</td>
                <td class="title-cell">
                  <div class="ellipsis ellipsis-2" :title="item.title">{{ item.title }}</div>
                  <div class="cell-sub ellipsis" :title="item.functionName ?? '-'">
                    {{ item.functionName ? `fn: ${item.functionName}` : '-' }}
                  </div>
                </td>
                <td>
                  <span
                    class="status-tag"
                    :class="statusToneClass(getDifficultyStatus(item.difficulty).tone)"
                    :title="getDifficultyStatus(item.difficulty).label"
                  >
                    <span class="status-icon" aria-hidden="true" />
                    {{ getDifficultyStatus(item.difficulty).label }}
                  </span>
                </td>
                <td class="type-cell">
                  <span class="ellipsis" :title="item.type ?? '-'">{{ item.type ?? '-' }}</span>
                </td>
                <td class="tag-cell">
                  <span class="ellipsis" :title="formatTagNames(item.tags)">{{ formatTagNames(item.tags) }}</span>
                </td>
                <td class="date-cell">{{ formatDateTime(item.updateTime) }}</td>
                <td class="ops">
                  <button class="link-btn" @click="openDualDetail(item.id)">Dual Detail</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button class="btn btn-light" :disabled="loading || batchDeleting || current <= 1" @click="changePage(current - 1)">
            Prev
          </button>
          <span class="pagination-meta">Page {{ current }} / {{ totalPagesDisplay }}, Total {{ total }}</span>
          <button class="btn btn-light" :disabled="loading || batchDeleting || current >= totalPagesDisplay" @click="changePage(current + 1)">
            Next
          </button>
        </div>
      </div>

      <div
        v-if="batchResult || batchErrorMessage"
        class="batch-result"
        :class="batchResult && batchResult.failureCount > 0 ? 'batch-result-warning' : (batchErrorMessage ? 'batch-result-error' : 'batch-result-success')"
        :role="batchErrorMessage ? 'alert' : 'status'"
        :aria-live="batchErrorMessage ? 'assertive' : 'polite'"
        aria-atomic="true"
      >
        <p v-if="batchResult">
          Batch delete finished: success {{ batchResult.successCount }}, failure {{ batchResult.failureCount }}
        </p>
        <p v-if="batchErrorMessage" class="batch-result-error-text">{{ batchErrorMessage }}</p>
        <ul v-if="batchFailures.length > 0">
          <li v-for="item in batchFailures" :key="item.questionId">
            Question {{ item.questionId }}: {{ item.message }}
          </li>
        </ul>
      </div>
    </section>

    <div v-if="detailVisible" class="modal-mask" @click.self="closeDualDetail">
      <section
        class="modal modal-large"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dual-detail-modal-title"
      >
        <header class="modal-header">
          <h2 id="dual-detail-modal-title">Dual Detail (Question {{ detailQuestionId }})</h2>
          <button class="link-btn" @click="closeDualDetail">Close</button>
        </header>

        <div v-if="detailLoading" class="state">Loading detail...</div>
        <div v-else-if="detailError" class="state state-error">{{ detailError }}</div>
        <div v-else-if="detailData" class="compare">
          <article class="card">
            <h3>MySQL</h3>
            <p><strong>Title:</strong> {{ detailData.mysqlQuestion.title }}</p>
            <p><strong>Type:</strong> {{ detailData.mysqlQuestion.type ?? '-' }}</p>
            <p>
              <strong>Difficulty:</strong>
              <span
                class="status-tag"
                :class="statusToneClass(getDifficultyStatus(detailData.mysqlQuestion.difficulty).tone)"
              >
                <span class="status-icon" aria-hidden="true" />
                {{ getDifficultyStatus(detailData.mysqlQuestion.difficulty).label }}
              </span>
            </p>
            <p><strong>Tags:</strong> {{ formatMysqlTags(detailData.mysqlQuestion.tags) }}</p>
            <p>
              <strong>Sync Status:</strong>
              <span
                class="status-tag"
                :class="statusToneClass(getSyncStatusMeta(detailData.mysqlQuestion.vectorSyncStatus).tone)"
              >
                <span class="status-icon" aria-hidden="true" />
                {{ getSyncStatusMeta(detailData.mysqlQuestion.vectorSyncStatus).label }}
              </span>
            </p>
            <p class="ellipsis" :title="detailData.mysqlQuestion.vectorSyncErrorMessage ?? '-'">
              <strong>Sync Error:</strong> {{ detailData.mysqlQuestion.vectorSyncErrorMessage ?? '-' }}
            </p>
            <p><strong>Updated At:</strong> {{ formatDateTime(detailData.mysqlQuestion.updateTime) }}</p>
          </article>

          <article class="card">
            <h3>Vector Projection</h3>
            <template v-if="currentProjection">
              <p><strong>Title:</strong> {{ currentProjection.title ?? '-' }}</p>
              <p><strong>Difficulty:</strong> {{ currentProjection.difficulty ?? '-' }}</p>
              <p><strong>Language:</strong> {{ currentProjection.language ?? '-' }}</p>
              <p><strong>Tags:</strong> {{ formatProjectionTags(currentProjection.tagsJson) }}</p>
              <p>
                <strong>Sync Status:</strong>
                <span
                  class="status-tag"
                  :class="statusToneClass(getSyncStatusMeta(currentProjection.syncStatus).tone)"
                >
                  <span class="status-icon" aria-hidden="true" />
                  {{ getSyncStatusMeta(currentProjection.syncStatus).label }}
                </span>
              </p>
              <p class="ellipsis" :title="currentProjection.syncErrorMessage ?? '-'">
                <strong>Sync Error:</strong> {{ currentProjection.syncErrorMessage ?? '-' }}
              </p>
              <p><strong>Updated At:</strong> {{ formatDateTime(currentProjection.updateTime) }}</p>
            </template>
            <p v-else>No projection data</p>
          </article>
        </div>

        <section class="doc-section">
          <h3>Question Documents</h3>
          <div class="toolbar">
            <input
              ref="documentInputRef"
              class="input"
              type="file"
              accept="application/pdf,.md,.markdown,.zip"
              @change="onDocumentFileChange"
            >
            <button
              class="btn"
              :disabled="documentUploading || !selectedDocumentFile || detailQuestionId == null"
              @click="onUploadQuestionDocument"
            >
              {{ documentUploading ? 'Uploading...' : 'Upload Document' }}
            </button>
            <button
              class="btn btn-light"
              :disabled="documentLoading || documentDeletingDocId != null || detailQuestionId == null"
              @click="refreshDocuments"
            >
              Refresh Documents
            </button>
          </div>

          <p v-if="documentError" class="state state-error">{{ documentError }}</p>
          <div v-else-if="documentLoading" class="state">Loading documents...</div>
          <div v-else-if="documentRows.length === 0" class="state">No documents</div>
          <div v-else class="table-shell table-shell-docs">
            <table class="table table-docs">
              <thead>
                <tr>
                  <th class="numeric">Doc ID</th>
                  <th>Title</th>
                  <th>Status</th>
                  <th class="numeric">Progress</th>
                  <th class="date-cell">Updated At</th>
                  <th>Converted URL</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="doc in documentRows" :key="doc.docId">
                  <td class="numeric">{{ doc.docId }}</td>
                  <td class="doc-title-cell">
                    <span class="ellipsis" :title="doc.docTitle ?? '-'">{{ doc.docTitle ?? '-' }}</span>
                  </td>
                  <td>
                    <span
                      class="status-tag"
                      :class="statusToneClass(getDocumentStatusMeta(doc.status).tone)"
                      :title="getDocumentStatusMeta(doc.status).label"
                    >
                      <span class="status-icon" aria-hidden="true" />
                      {{ getDocumentStatusMeta(doc.status).label }}
                    </span>
                  </td>
                  <td class="numeric">{{ formatProgress(doc.progressPercent) }}</td>
                  <td class="date-cell">{{ formatDateTime(doc.updatedAt ?? undefined) }}</td>
                  <td>
                    <a v-if="doc.convertedDocUrl" :href="doc.convertedDocUrl" target="_blank" rel="noreferrer">
                      <span class="ellipsis" :title="doc.convertedDocUrl">Open</span>
                    </a>
                    <span v-else>-</span>
                  </td>
                  <td class="ops">
                    <button
                      v-if="canViewSegments(doc)"
                      class="link-btn"
                      :disabled="documentDeletingDocId != null"
                      @click="openDocumentSegmentDetail(doc)"
                    >
                      View Segments
                    </button>
                    <button
                      class="link-btn"
                      :disabled="documentDeletingDocId != null"
                      @click="onDeleteQuestionDocument(doc)"
                    >
                      {{ documentDeletingDocId === doc.docId ? 'Deleting...' : 'Delete Document' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </section>
    </div>

    <div v-if="createVisible" class="modal-mask" @click.self="closeCreate">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-question-modal-title"
      >
        <header class="modal-header">
          <h2 id="create-question-modal-title">Create Question</h2>
          <button class="link-btn" @click="closeCreate">Close</button>
        </header>

        <form class="edit-form" @submit.prevent="onSubmitCreate">
          <label>
            Title
            <input v-model="createForm.title" class="input" type="text" maxlength="200" required>
          </label>

          <label>
            Description
            <textarea v-model="createForm.description" class="textarea" rows="4" maxlength="20000" required />
          </label>

          <label>
            Difficulty
            <select v-model="createForm.difficulty" class="select">
              <option v-for="level in difficultyOptions" :key="level" :value="level">{{ level }}</option>
            </select>
          </label>

          <label>
            System Tags (multi-select, optional)
            <select v-model="createForm.selectedTagIds" class="select select-multiple" multiple :disabled="createTagLoading">
              <option v-for="tag in availableTags" :key="tag.id" :value="tag.id">
                {{ tag.tagName }}
              </option>
            </select>
            <small class="hint">{{ createTagLoading ? 'Loading tags...' : 'Use Ctrl/Cmd to select multiple tags' }}</small>
          </label>

          <label>
            Custom Tags (comma separated, optional)
            <input
              v-model="createForm.customTagsInput"
              class="input"
              type="text"
              placeholder="array,hash-table"
            >
          </label>

          <label>
            Shared Function Name
            <input v-model="createForm.sharedFunctionName" class="input" type="text" maxlength="128" required>
          </label>

          <label>
            Shared Test Cases (JSON Array)
            <textarea
              v-model="createForm.sharedTestCases"
              class="textarea"
              rows="6"
              placeholder='[{"input":{"nums":[2,7,11,15],"target":9},"expectedOutput":[0,1]}]'
              required
            />
          </label>

          <div class="template-grid">
            <article class="template-card">
              <h3>JAVA Template</h3>
              <label>
                Function Name
                <input v-model="createForm.javaFunctionName" class="input" type="text" maxlength="128" required>
              </label>
              <label>
                Code Skeleton
                <textarea v-model="createForm.javaCodeSkeleton" class="textarea" rows="5" maxlength="20000" required />
              </label>
              <label>
                Reference Answer
                <textarea v-model="createForm.javaReferenceAnswer" class="textarea" rows="5" maxlength="20000" required />
              </label>
            </article>

            <article class="template-card">
              <h3>PYTHON Template</h3>
              <label>
                Function Name
                <input v-model="createForm.pythonFunctionName" class="input" type="text" maxlength="128" required>
              </label>
              <label>
                Code Skeleton
                <textarea v-model="createForm.pythonCodeSkeleton" class="textarea" rows="5" maxlength="20000" required />
              </label>
              <label>
                Reference Answer
                <textarea v-model="createForm.pythonReferenceAnswer" class="textarea" rows="5" maxlength="20000" required />
              </label>
            </article>

            <article class="template-card">
              <h3>JAVASCRIPT Template</h3>
              <label>
                Function Name
                <input v-model="createForm.javascriptFunctionName" class="input" type="text" maxlength="128" required>
              </label>
              <label>
                Code Skeleton
                <textarea v-model="createForm.javascriptCodeSkeleton" class="textarea" rows="5" maxlength="20000" required />
              </label>
              <label>
                Reference Answer
                <textarea v-model="createForm.javascriptReferenceAnswer" class="textarea" rows="5" maxlength="20000" required />
              </label>
            </article>
          </div>

          <p v-if="createError" class="state state-error">{{ createError }}</p>

          <div class="actions">
            <button class="btn btn-light" type="button" :disabled="creating" @click="closeCreate">Cancel</button>
            <button class="btn" type="submit" :disabled="creating">
              {{ creating ? 'Creating...' : 'Create' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  adminQuestionApi,
  type AdminBatchDeleteItemResult,
  type AdminBatchDeleteResponse,
  type AdminDifficulty,
  type AdminQuestionCreateRequest,
  type AdminQuestionDocumentVO,
  type AdminQuestionDualDetail,
  type AdminQuestionListItem,
  type AdminQuestionProjection,
  type AdminStandardCase,
  type AdminQuestionTag,
  type AdminQuestionType,
} from '@/api/adminQuestion'
import { buildQuestionDocumentSegmentRoute } from '@/views/questionDocumentRoute'

const router = useRouter()

const difficultyOptions: AdminDifficulty[] = ['SIMPLE', 'MEDIUM', 'HARD']
const questionTypeOptions: AdminQuestionType[] = ['SYSTEM', 'AI']

const keyword = ref('')
const difficulty = ref<AdminDifficulty | ''>('')
const questionType = ref<AdminQuestionType | ''>('')
const current = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = ref(1)
const questions = ref<AdminQuestionListItem[]>([])
const selectedIds = ref<number[]>([])

const loading = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')

const batchDeleting = ref(false)
const batchResult = ref<AdminBatchDeleteResponse | null>(null)
const batchErrorMessage = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailData = ref<AdminQuestionDualDetail | null>(null)
const detailQuestionId = ref<number | null>(null)
const detailRequestToken = ref(0)

const documentRows = ref<AdminQuestionDocumentVO[]>([])
const documentLoading = ref(false)
const documentUploading = ref(false)
const documentDeletingDocId = ref<number | null>(null)
const documentError = ref('')
const selectedDocumentFile = ref<File | null>(null)
const documentInputRef = ref<HTMLInputElement | null>(null)
let documentPollTimer: number | null = null
const terminalDocumentStatuses = new Set(['VECTOR_STORED', 'FAILED'])
const segmentReadyStatuses = new Set(['CHUNKED', 'VECTOR_STORED'])

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createTagLoading = ref(false)
const availableTags = ref<AdminQuestionTag[]>([])
const createForm = reactive({
  title: '',
  description: '',
  difficulty: 'SIMPLE' as AdminDifficulty,
  selectedTagIds: [] as number[],
  customTagsInput: '',
  sharedFunctionName: '',
  sharedTestCases: '',
  javaFunctionName: '',
  javaCodeSkeleton: '',
  javaReferenceAnswer: '',
  pythonFunctionName: '',
  pythonCodeSkeleton: '',
  pythonReferenceAnswer: '',
  javascriptFunctionName: '',
  javascriptCodeSkeleton: '',
  javascriptReferenceAnswer: '',
})

type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'
interface StatusMeta {
  label: string
  tone: StatusTone
}

const selectedIdSet = computed(() => new Set(selectedIds.value))
const selectedOnPageCount = computed(() => {
  return questions.value.reduce((count, item) => count + (selectedIdSet.value.has(item.id) ? 1 : 0), 0)
})
const allSelectedOnPage = computed(() => questions.value.length > 0 && selectedOnPageCount.value === questions.value.length)
const partiallySelectedOnPage = computed(() => selectedOnPageCount.value > 0 && !allSelectedOnPage.value)
const skeletonRowCount = computed(() => Math.min(Math.max(pageSize.value, 6), 10))
const totalPagesDisplay = computed(() => (totalPages.value > 0 ? totalPages.value : 1))
const batchFailures = computed<AdminBatchDeleteItemResult[]>(() => {
  if (!batchResult.value) {
    return []
  }
  return batchResult.value.results.filter((item) => !item.success)
})
// 汇总批量删除失败的题目 id，用于失败后快速重试。
const failedBatchQuestionIds = computed<number[]>(() => {
  if (!batchResult.value) {
    return []
  }
  const failedIds = batchResult.value.results
    .filter((item) => !item.success)
    .map((item) => item.questionId)
    .filter((id): id is number => typeof id === 'number')
  return Array.from(new Set(failedIds))
})
// 统一批量删除按钮文案，便于在不同状态下提供明确反馈。
const batchDeleteButtonText = computed(() => {
  if (batchDeleting.value) {
    return `Deleting (${selectedIds.value.length})...`
  }
  return `Delete Selected (${selectedIds.value.length})`
})
const currentProjection = computed<AdminQuestionProjection | null>(() => {
  if (!detailData.value) {
    return null
  }
  return detailData.value.vectorProjection ?? detailData.value.postgresProjection ?? null
})

const getErrorMessage = (error: unknown) => (error instanceof Error ? error.message : 'Operation failed')
const statusToneClass = (tone: StatusTone) => `status-${tone}`

const isRowSelected = (questionId: number) => selectedIdSet.value.has(questionId)

const toStatusLabel = (rawStatus: string) => rawStatus.replace(/_/g, ' ')

const getDifficultyStatus = (value: string | null | undefined): StatusMeta => {
  if (!value) {
    return { label: '-', tone: 'neutral' }
  }
  if (value === 'SIMPLE') {
    return { label: 'SIMPLE', tone: 'success' }
  }
  if (value === 'MEDIUM') {
    return { label: 'MEDIUM', tone: 'warning' }
  }
  return { label: 'HARD', tone: 'danger' }
}

const getSyncStatusMeta = (status: string | null | undefined): StatusMeta => {
  if (!status) {
    return { label: '-', tone: 'neutral' }
  }
  const normalized = status.trim().toUpperCase()
  if (normalized.length === 0) {
    return { label: '-', tone: 'neutral' }
  }
  if (normalized.includes('FAIL') || normalized.includes('ERROR')) {
    return { label: toStatusLabel(normalized), tone: 'danger' }
  }
  if (
    normalized.includes('PENDING')
    || normalized.includes('PROCESS')
    || normalized.includes('RUNNING')
    || normalized.includes('QUEUE')
    || normalized.includes('WAIT')
    || normalized.includes('CHUNK')
  ) {
    return { label: toStatusLabel(normalized), tone: 'warning' }
  }
  if (
    normalized.includes('SUCCESS')
    || normalized.includes('SYNCED')
    || normalized.includes('STORED')
    || normalized.includes('DONE')
    || normalized.includes('COMPLETED')
  ) {
    return { label: toStatusLabel(normalized), tone: 'success' }
  }
  return { label: toStatusLabel(normalized), tone: 'info' }
}

const getDocumentStatusMeta = (status: string | null | undefined) => getSyncStatusMeta(status)

const parseCustomTagsInput = (raw: string) => {
  const values = raw
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
  return values.length > 0 ? Array.from(new Set(values)) : undefined
}

const parseStandardCasePool = (raw: string): AdminStandardCase[] | null => {
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return null
    }
    const casePool: AdminStandardCase[] = []
    for (const item of parsed) {
      if (!item || typeof item !== 'object' || Array.isArray(item)) {
        return null
      }
      const current = item as Record<string, unknown>
      if (typeof current.stdin === 'string' && typeof current.expectedStdout === 'string') {
        casePool.push({
          stdin: current.stdin,
          expectedStdout: current.expectedStdout,
          publicCase: typeof current.publicCase === 'boolean' ? current.publicCase : true,
          description: typeof current.description === 'string' ? current.description : undefined,
        })
        continue
      }
      if ('input' in current && 'expectedOutput' in current) {
        casePool.push({
          stdin: typeof current.input === 'string' ? current.input : JSON.stringify(current.input),
          expectedStdout: typeof current.expectedOutput === 'string'
            ? current.expectedOutput
            : JSON.stringify(current.expectedOutput),
          publicCase: true,
          description: typeof current.description === 'string' ? current.description : undefined,
        })
        continue
      }
      return null
    }
    return casePool
  } catch {
    return null
  }
}

const formatTagNames = (tags: { tagName: string }[] | null | undefined) => {
  if (!tags || tags.length === 0) {
    return '-'
  }
  return tags.map((item) => item.tagName).join(', ')
}

const formatMysqlTags = (tags: string[] | null | undefined) => {
  if (!tags || tags.length === 0) {
    return '-'
  }
  return tags.join(', ')
}

const formatProjectionTags = (tagsJson: string | null | undefined) => {
  if (!tagsJson) {
    return '-'
  }
  try {
    const parsed = JSON.parse(tagsJson) as unknown
    if (Array.isArray(parsed)) {
      const names = parsed.filter((item): item is string => typeof item === 'string')
      return names.length > 0 ? names.join(', ') : '-'
    }
    return '-'
  } catch {
    return tagsJson
  }
}

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ')
}

const formatProgress = (value: number | null | undefined) => {
  if (value == null) {
    return '0%'
  }
  if (value < 0) {
    return 'FAILED'
  }
  return `${value}%`
}

const stopDocumentPolling = () => {
  if (documentPollTimer != null) {
    window.clearInterval(documentPollTimer)
    documentPollTimer = null
  }
}

const shouldPollDocuments = (docs: AdminQuestionDocumentVO[]) => {
  return docs.some((doc) => {
    if (!doc.status) {
      return true
    }
    return !terminalDocumentStatuses.has(doc.status)
  })
}

const loadQuestionDocuments = async (questionId: number) => {
  documentLoading.value = true
  documentError.value = ''
  try {
    documentRows.value = await adminQuestionApi.listQuestionDocuments(questionId)
  } catch (error) {
    documentRows.value = []
    documentError.value = getErrorMessage(error)
  } finally {
    documentLoading.value = false
  }
}

const startDocumentPolling = (questionId: number) => {
  stopDocumentPolling()
  if (!shouldPollDocuments(documentRows.value)) {
    return
  }
  documentPollTimer = window.setInterval(async () => {
    await loadQuestionDocuments(questionId)
    if (!shouldPollDocuments(documentRows.value)) {
      stopDocumentPolling()
    }
  }, 3000)
}

const resetDocumentState = () => {
  stopDocumentPolling()
  documentRows.value = []
  documentLoading.value = false
  documentUploading.value = false
  documentDeletingDocId.value = null
  documentError.value = ''
  selectedDocumentFile.value = null
  if (documentInputRef.value) {
    documentInputRef.value.value = ''
  }
}

const loadQuestions = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await adminQuestionApi.pageQuestions({
      pageNum: current.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      difficulty: difficulty.value || undefined,
      type: questionType.value || undefined,
    })
    questions.value = data.records
    total.value = data.total
    totalPages.value = data.pages > 0 ? data.pages : 1
    current.value = data.current
    selectedIds.value = []
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
    questions.value = []
    total.value = 0
    totalPages.value = 1
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

const loadCreateTags = async () => {
  createTagLoading.value = true
  try {
    availableTags.value = await adminQuestionApi.listTags()
  } catch {
    availableTags.value = []
  } finally {
    createTagLoading.value = false
  }
}

// 回填失败题目 id 选择，保留“再次删除”的最小可行路径。
const restoreFailedBatchSelection = () => {
  if (failedBatchQuestionIds.value.length === 0) {
    return
  }
  selectedIds.value = [...failedBatchQuestionIds.value]
}

// 清理列表上下文切换时不应保留的批量反馈信息。
const clearStaleListFeedback = () => {
  batchResult.value = null
  batchErrorMessage.value = ''
  noticeMessage.value = ''
}

const changePage = async (page: number) => {
  if (batchDeleting.value || page < 1 || page > totalPagesDisplay.value || page === current.value) {
    return
  }
  clearStaleListFeedback()
  current.value = page
  await loadQuestions()
}

const onSearch = async () => {
  clearStaleListFeedback()
  current.value = 1
  await loadQuestions()
}

const onReset = async () => {
  clearStaleListFeedback()
  keyword.value = ''
  difficulty.value = ''
  questionType.value = ''
  current.value = 1
  await loadQuestions()
}

const getCurrentPageIds = () => questions.value.map((item) => item.id)

const selectCurrentPage = () => {
  selectedIds.value = getCurrentPageIds()
}

const invertCurrentPageSelection = () => {
  const pageIds = getCurrentPageIds()
  const pageIdSet = new Set(pageIds)
  const preserved = selectedIds.value.filter((id) => !pageIdSet.has(id))
  const selectedSet = new Set(selectedIds.value)
  const inverted = pageIds.filter((id) => !selectedSet.has(id))
  selectedIds.value = [...preserved, ...inverted]
}

const clearSelection = () => {
  selectedIds.value = []
}

const onToggleAll = (event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    selectCurrentPage()
    return
  }
  clearSelection()
}

const onToggleSingle = (questionId: number, event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    if (!selectedIds.value.includes(questionId)) {
      selectedIds.value = [...selectedIds.value, questionId]
    }
    return
  }
  selectedIds.value = selectedIds.value.filter((id) => id !== questionId)
}

const escapeCsvValue = (value: string | number | null | undefined) => {
  const normalized = value == null ? '-' : String(value)
  if (!/[",\n]/.test(normalized)) {
    return normalized
  }
  return `"${normalized.replace(/"/g, '""')}"`
}

const buildExportFileName = () => {
  const now = new Date()
  const date = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  const time = `${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`
  return `question-export-p${current.value}-${date}-${time}.csv`
}

const onBatchExport = () => {
  if (selectedIds.value.length === 0) {
    noticeMessage.value = 'Please select at least one row before exporting CSV'
    return
  }
  const selectedSet = new Set(selectedIds.value)
  const rows = questions.value.filter((item) => selectedSet.has(item.id))
  if (rows.length === 0) {
    noticeMessage.value = 'No selected rows on current page'
    return
  }

  const lines = [['ID', 'Title', 'Type', 'Difficulty', 'Tags', 'Updated At'].join(',')]
  rows.forEach((item) => {
    lines.push([
      escapeCsvValue(item.id),
      escapeCsvValue(item.title),
      escapeCsvValue(item.type ?? '-'),
      escapeCsvValue(item.difficulty ?? '-'),
      escapeCsvValue(formatTagNames(item.tags)),
      escapeCsvValue(formatDateTime(item.updateTime)),
    ].join(','))
  })

  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' })
  const url = window.URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = buildExportFileName()
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => {
    window.URL.revokeObjectURL(url)
  }, 0)
  noticeMessage.value = `Exported ${rows.length} selected question(s) to CSV`
}

const onBatchDelete = async () => {
  if (selectedIds.value.length === 0) {
    return
  }
  // 固化本次删除目标，避免异步期间选择集变化造成提示与执行不一致。
  const deleteIds = [...selectedIds.value]
  const confirmed = window.confirm(`Delete ${deleteIds.length} selected questions?`)
  if (!confirmed) {
    return
  }
  batchDeleting.value = true
  batchResult.value = null
  batchErrorMessage.value = ''
  noticeMessage.value = `Deleting ${deleteIds.length} selected question(s)...`
  try {
    const result = await adminQuestionApi.batchDeleteQuestions(deleteIds)
    batchResult.value = result
    if (result.failureCount === 0) {
      noticeMessage.value = `Batch delete completed: deleted ${result.successCount} question(s)`
      batchErrorMessage.value = ''
    } else if (result.successCount === 0) {
      noticeMessage.value = `Batch delete failed for all ${result.failureCount} question(s), see details below`
      batchErrorMessage.value = 'Batch delete encountered errors, failed items have been re-selected for retry'
    } else {
      noticeMessage.value = `Batch delete partially completed: success ${result.successCount}, failure ${result.failureCount}`
      batchErrorMessage.value = 'Some questions failed to delete, failed items have been re-selected for retry'
    }
    await loadQuestions()
    if (result.failureCount > 0) {
      restoreFailedBatchSelection()
    }
  } catch (error) {
    batchErrorMessage.value = getErrorMessage(error)
    noticeMessage.value = 'Batch delete request failed, see details below'
  } finally {
    batchDeleting.value = false
  }
}

const fetchDualDetail = (questionId: number) => adminQuestionApi.getDualDetail(questionId)

const openDualDetail = async (questionId: number) => {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detailData.value = null
  detailQuestionId.value = questionId
  resetDocumentState()
  detailRequestToken.value += 1
  const token = detailRequestToken.value

  try {
    const data = await fetchDualDetail(questionId)
    if (token !== detailRequestToken.value) {
      return
    }
    detailData.value = data
    await loadQuestionDocuments(questionId)
    startDocumentPolling(questionId)
  } catch (error) {
    if (token !== detailRequestToken.value) {
      return
    }
    detailError.value = getErrorMessage(error)
  } finally {
    if (token === detailRequestToken.value) {
      detailLoading.value = false
    }
  }
}

const closeDualDetail = () => {
  detailVisible.value = false
  detailError.value = ''
  detailData.value = null
  detailQuestionId.value = null
  detailRequestToken.value += 1
  resetDocumentState()
}

const onDocumentFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    selectedDocumentFile.value = input.files.item(0) ?? null
    return
  }
  selectedDocumentFile.value = null
}

const onUploadQuestionDocument = async () => {
  if (detailQuestionId.value == null || !selectedDocumentFile.value) {
    return
  }
  documentUploading.value = true
  documentError.value = ''
  try {
    await adminQuestionApi.uploadQuestionDocument(detailQuestionId.value, selectedDocumentFile.value)
    noticeMessage.value = 'Document uploaded'
    selectedDocumentFile.value = null
    if (documentInputRef.value) {
      documentInputRef.value.value = ''
    }
    await loadQuestionDocuments(detailQuestionId.value)
    startDocumentPolling(detailQuestionId.value)
  } catch (error) {
    documentError.value = getErrorMessage(error)
  } finally {
    documentUploading.value = false
  }
}

const refreshDocuments = async () => {
  if (detailQuestionId.value == null) {
    return
  }
  await loadQuestionDocuments(detailQuestionId.value)
  startDocumentPolling(detailQuestionId.value)
}

const onDeleteQuestionDocument = async (doc: AdminQuestionDocumentVO) => {
  const questionId = detailQuestionId.value
  if (questionId == null) {
    return
  }
  // 删除前明确提示会同步清理关联评测与向量数据。
  const confirmed = window.confirm(
    'Delete this document and all related segments, vectors, RAGAS, and HitK data? This action cannot be undone.',
  )
  if (!confirmed) {
    return
  }
  documentDeletingDocId.value = doc.docId
  documentError.value = ''
  try {
    await adminQuestionApi.deleteQuestionDocument(questionId, doc.docId)
    noticeMessage.value = 'Document deleted'
    await loadQuestionDocuments(questionId)
    startDocumentPolling(questionId)
  } catch (error) {
    documentError.value = getErrorMessage(error)
  } finally {
    documentDeletingDocId.value = null
  }
}

const canViewSegments = (doc: AdminQuestionDocumentVO) => {
  const status = (doc.status ?? '').trim().toUpperCase()
  return segmentReadyStatuses.has(status)
}

const openDocumentSegmentDetail = async (doc: AdminQuestionDocumentVO) => {
  const questionId = detailQuestionId.value
  if (questionId == null || !canViewSegments(doc)) {
    return
  }
  // 先缓存 questionId，再关闭弹窗，避免状态清空导致路由缺少必要参数。
  const targetRoute = buildQuestionDocumentSegmentRoute(questionId, doc.docId, doc.docTitle)
  closeDualDetail()
  await router.push(targetRoute)
}

const resetCreateForm = () => {
  createForm.title = ''
  createForm.description = ''
  createForm.difficulty = 'SIMPLE'
  createForm.selectedTagIds = []
  createForm.customTagsInput = ''
  createForm.sharedFunctionName = ''
  createForm.sharedTestCases = ''
  createForm.javaFunctionName = ''
  createForm.javaCodeSkeleton = ''
  createForm.javaReferenceAnswer = ''
  createForm.pythonFunctionName = ''
  createForm.pythonCodeSkeleton = ''
  createForm.pythonReferenceAnswer = ''
  createForm.javascriptFunctionName = ''
  createForm.javascriptCodeSkeleton = ''
  createForm.javascriptReferenceAnswer = ''
}

const openCreate = async () => {
  createVisible.value = true
  createError.value = ''
  resetCreateForm()
  await loadCreateTags()
}

const closeCreate = () => {
  createVisible.value = false
  createError.value = ''
  creating.value = false
}

const onSubmitCreate = async () => {
  const title = createForm.title.trim()
  const description = createForm.description.trim()
  const sharedTestCases = createForm.sharedTestCases.trim()
  const standardCasePool = parseStandardCasePool(sharedTestCases)
  const javaFunctionName = createForm.javaFunctionName.trim()
  const javaCodeSkeleton = createForm.javaCodeSkeleton.trim()
  const javaReferenceAnswer = createForm.javaReferenceAnswer.trim()
  const pythonFunctionName = createForm.pythonFunctionName.trim()
  const pythonCodeSkeleton = createForm.pythonCodeSkeleton.trim()
  const pythonReferenceAnswer = createForm.pythonReferenceAnswer.trim()
  const javascriptFunctionName = createForm.javascriptFunctionName.trim()
  const javascriptCodeSkeleton = createForm.javascriptCodeSkeleton.trim()
  const javascriptReferenceAnswer = createForm.javascriptReferenceAnswer.trim()
  const customTags = parseCustomTagsInput(createForm.customTagsInput)

  if (
    !title
    || !description
    || !sharedTestCases
    || !standardCasePool
    || !javaFunctionName
    || !javaCodeSkeleton
    || !javaReferenceAnswer
    || !pythonFunctionName
    || !pythonCodeSkeleton
    || !pythonReferenceAnswer
    || !javascriptFunctionName
    || !javascriptCodeSkeleton
    || !javascriptReferenceAnswer
  ) {
    createError.value = 'Please fill all required fields, including standard cases, and all template blocks'
    return
  }

  const payload: AdminQuestionCreateRequest = {
    title,
    description,
    difficulty: createForm.difficulty,
    standardCasePool,
    tagIds: createForm.selectedTagIds.length > 0 ? createForm.selectedTagIds : undefined,
    tags: customTags,
    codeTemplates: [
      {
        language: 'JAVA',
        entryMethodName: javaFunctionName,
        starterCode: javaCodeSkeleton,
        referenceAnswer: javaReferenceAnswer,
      },
      {
        language: 'PYTHON',
        entryMethodName: pythonFunctionName,
        starterCode: pythonCodeSkeleton,
        referenceAnswer: pythonReferenceAnswer,
      },
      {
        language: 'JAVASCRIPT',
        entryMethodName: javascriptFunctionName,
        starterCode: javascriptCodeSkeleton,
        referenceAnswer: javascriptReferenceAnswer,
      },
    ],
  }

  creating.value = true
  createError.value = ''
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await adminQuestionApi.createQuestion(payload)
    noticeMessage.value = 'Question created successfully'
    closeCreate()
    await loadQuestions()
  } catch (error) {
    createError.value = getErrorMessage(error)
  } finally {
    creating.value = false
  }
}

onMounted(async () => {
  await loadQuestions()
})

onBeforeUnmount(() => {
  stopDocumentPolling()
})
</script>

<style scoped>
.page {
  padding: 24px;
  color: #111827;
}


.panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.selection-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
}

.selection-summary {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: #334155;
  font-size: 14px;
}

.selection-summary strong {
  color: #1d4ed8;
  font-size: 13px;
  letter-spacing: 0.02em;
}

.muted {
  color: #64748b;
}

.selection-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.input,
.select,
.textarea {
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 14px;
}

.input,
.select {
  min-width: 160px;
}

.select-multiple {
  min-height: 120px;
}

.textarea {
  width: 100%;
  resize: vertical;
}

.btn {
  border: none;
  border-radius: 8px;
  padding: 8px 12px;
  background: #2563eb;
  color: #fff;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-light {
  background: #e5e7eb;
  color: #111827;
}

.btn-danger {
  background: #dc2626;
}

.notice {
  margin: 0 0 10px;
  padding: 8px 10px;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  background: #ecfdf5;
  color: #065f46;
}

.state {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  color: #374151;
}

.state-error {
  color: #b91c1c;
}

.state-empty {
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  border-style: solid;
  background: #f8fafc;
}

.state-empty p {
  margin: 0;
}

.table-shell {
  width: 100%;
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}

.table-shell-loading {
  border-color: #dbeafe;
  background: #f8fbff;
}

.table-loading-hint {
  margin: 0;
  padding: 10px 12px;
  border-bottom: 1px solid #dbeafe;
  color: #334155;
  font-size: 13px;
  background: linear-gradient(90deg, #f0f7ff 25%, #f8fbff 50%, #f0f7ff 75%);
  background-size: 240% 100%;
  animation: skeleton-shimmer 1.6s linear infinite;
}

.table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

.table-main {
  min-width: 1120px;
  table-layout: fixed;
}

.table-docs {
  min-width: 980px;
  table-layout: fixed;
}

.table th,
.table td {
  border-bottom: 1px solid #e5e7eb;
  padding: 10px 8px;
  text-align: left;
  vertical-align: top;
}

.table thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  background: #f8fafc;
  white-space: nowrap;
}

.table tbody tr:hover {
  background: #f8fafc;
}

.row-selected {
  background: #eff6ff;
}

.checkbox-cell {
  width: 44px;
  text-align: center !important;
  vertical-align: middle !important;
}

.numeric {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.date-cell {
  text-align: center !important;
  white-space: nowrap;
}

.title-cell {
  min-width: 280px;
  max-width: 420px;
}

.type-cell {
  max-width: 180px;
}

.tag-cell {
  max-width: 260px;
}

.doc-title-cell {
  max-width: 280px;
}

.cell-sub {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: top;
}

.title-cell .ellipsis,
.type-cell .ellipsis,
.tag-cell .ellipsis,
.doc-title-cell .ellipsis {
  display: block;
}

.ellipsis-2 {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
}

.status-icon {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
  flex: 0 0 auto;
}

.status-success {
  color: #166534;
  background: #dcfce7;
  border-color: #86efac;
}

.status-warning {
  color: #92400e;
  background: #fef3c7;
  border-color: #fcd34d;
}

.status-danger {
  color: #991b1b;
  background: #fee2e2;
  border-color: #fca5a5;
}

.status-info {
  color: #1d4ed8;
  background: #dbeafe;
  border-color: #93c5fd;
}

.status-neutral {
  color: #475569;
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.skeleton-line {
  display: block;
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(90deg, #e5e7eb 25%, #f3f4f6 50%, #e5e7eb 75%);
  background-size: 220% 100%;
  animation: skeleton-shimmer 1.3s ease-in-out infinite;
}

.skeleton-check {
  width: 14px;
  height: 14px;
  margin: 0 auto;
}

.skeleton-id {
  width: 50px;
  margin-left: auto;
}

.skeleton-tag {
  width: 88px;
}

.skeleton-date {
  width: 150px;
  margin: 0 auto;
}

.skeleton-action {
  width: 96px;
}

.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.link-btn {
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  padding: 0;
}

.pagination {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.batch-result {
  margin-top: 12px;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
  font-size: 14px;
}

.batch-result-success {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}

.batch-result-warning {
  border-color: #fcd34d;
  background: #fffbeb;
  color: #92400e;
}

.batch-result-error {
  border-color: #fca5a5;
  background: #fef2f2;
  color: #991b1b;
}

.batch-result p {
  margin: 0;
}

.batch-result-error-text {
  margin-top: 8px;
}

.batch-result ul {
  margin: 8px 0 0;
  padding-left: 18px;
  max-height: 180px;
  overflow: auto;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal {
  width: min(920px, 100%);
  max-height: 90vh;
  overflow: auto;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
}

.modal-large {
  width: min(1080px, 100%);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
}

.card h3 {
  margin: 0 0 10px;
}

.card p {
  margin: 6px 0;
  font-size: 14px;
}

.doc-section {
  margin-top: 16px;
}

.doc-section h3 {
  margin: 0 0 10px;
}

.edit-form {
  display: grid;
  gap: 10px;
}

.edit-form label {
  display: grid;
  gap: 6px;
  font-size: 14px;
}

.hint {
  color: #6b7280;
  font-size: 12px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.template-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px;
  display: grid;
  gap: 8px;
}

.template-card h3 {
  margin: 0;
  font-size: 14px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (max-width: 960px) {
  .page {
    padding: 16px;
  }

  .selection-toolbar {
    align-items: flex-start;
  }

  .selection-actions {
    width: 100%;
  }

  .compare,
  .template-grid {
    grid-template-columns: 1fr;
  }

  .ops {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* UI override: QuestionManagement data-dense layout */
.page {
  --qm-bg: #f8fafc;
  --qm-panel: #ffffff;
  --qm-text: #1e293b;
  --qm-muted: #64748b;
  --qm-border: #dbe3ef;
  --qm-border-strong: #cbd5e1;
  --qm-primary: #2563eb;
  --qm-primary-soft: #eff6ff;
  --qm-danger: #dc2626;
  color: var(--qm-text);
  background: radial-gradient(1200px 420px at 0 -15%, #eaf2ff 0%, rgba(234, 242, 255, 0) 62%), var(--qm-bg);
  border-radius: var(--radius-md);
}

.panel {
  border-color: var(--qm-border);
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.05);
}

.panel > .toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 2.2fr) repeat(2, minmax(150px, 1fr)) repeat(4, auto);
  gap: 10px;
  padding: 10px;
  margin-bottom: 14px;
  border: 1px solid #e7eef8;
  border-radius: 10px;
  background: #fbfdff;
}

.panel > .toolbar > .input,
.panel > .toolbar > .select {
  min-width: 0;
  height: 36px;
}

.input,
.select,
.textarea {
  border-color: var(--qm-border-strong);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.btn {
  min-height: 36px;
  font-weight: 600;
  transition: transform 0.12s ease, filter 0.2s ease, box-shadow 0.2s ease;
}

.btn:hover:not(:disabled) {
  filter: brightness(0.98);
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.18);
}

.btn:active:not(:disabled) {
  transform: translateY(1px);
}

.btn-primary-action {
  background: var(--qm-primary);
  color: #fff;
}

.btn-create-action {
  background: #f0f9ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
}

.selection-toolbar {
  border-color: #bfdbfe;
  background: linear-gradient(180deg, #f8fbff 0%, #f2f7ff 100%);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.5);
}

.selection-summary {
  color: #334155;
}

.selection-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--qm-primary-soft);
  color: #1d4ed8;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.selection-actions .btn {
  border: 1px solid #dbe7ff;
}

.notice,
.batch-result {
  border-left: 4px solid transparent;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.notice {
  border-left-color: #22c55e;
}

.table-shell {
  border-color: var(--qm-border);
  border-radius: 12px;
}

.table th,
.table td {
  padding: 12px 10px;
  line-height: 1.45;
  border-bottom-color: #e8edf4;
}

.table thead th {
  background: #f4f8ff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.table tbody tr:nth-child(even) {
  background: #fcfdff;
}

.table tbody tr:hover {
  background: #f5f9ff;
}

.row-selected {
  background: #eff6ff !important;
  box-shadow: inset 3px 0 0 var(--qm-primary);
}

.cell-sub {
  color: #475569;
}

.status-tag {
  font-weight: 700;
  letter-spacing: 0.01em;
}

.ops .link-btn {
  padding: 4px 10px;
  border-radius: 8px;
  background: #eef2ff;
  color: #1d4ed8;
  text-decoration: none;
}

.ops .link-btn:hover {
  background: #dbeafe;
}

.pagination {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid #e5eaf1;
  justify-content: space-between;
}

.pagination-meta {
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.batch-result {
  margin-top: 14px;
  border-color: #d8e3f2;
}

.batch-result-success {
  border-left-color: #16a34a;
}

.batch-result-warning {
  border-left-color: #d97706;
}

.batch-result-error {
  border-left-color: #dc2626;
}

.input:focus-visible,
.select:focus-visible,
.textarea:focus-visible,
.btn:focus-visible,
.link-btn:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

@media (max-width: 1200px) {
  .panel > .toolbar {
    display: flex;
    flex-wrap: wrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  .btn,
  .input,
  .select,
  .textarea,
  .link-btn {
    transition: none !important;
  }
}

/* Pixel tune v2: micro rhythm and hierarchy */
.page {
  padding: 22px;
  gap: 14px;
}

.panel {
  padding: 18px 16px 16px;
}

.panel > .toolbar {
  grid-template-columns: minmax(260px, 2.4fr) repeat(2, minmax(148px, 1fr)) auto auto auto auto;
  gap: 9px;
  margin-bottom: 12px;
}

.panel > .toolbar > .btn {
  min-width: 96px;
}

.selection-toolbar {
  padding: 12px;
  margin-bottom: 10px;
}

.selection-actions .btn {
  min-height: 34px;
}

.table thead th {
  padding: 11px 10px;
  font-size: 11.5px;
}

.table td {
  padding: 10px 10px;
}

.ops .link-btn {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  font-weight: 700;
}

.pagination-meta {
  font-weight: 600;
  letter-spacing: 0.01em;
}

.batch-result ul {
  padding-left: 20px;
}

.modal {
  border: 1px solid #ccdaf1;
  box-shadow: 0 18px 40px rgba(14, 39, 79, 0.16);
}

.edit-form label {
  color: #304562;
}

.template-card {
  background: #fbfdff;
}

@media (max-width: 1200px) {
  .panel > .toolbar > .btn {
    min-width: 120px;
  }
}

@media (max-width: 960px) {
  .selection-actions .btn {
    flex: 1 1 180px;
  }
}

@media (max-width: 760px) {
  .page {
    padding: 14px;
  }
}

.page-header {
  display: grid;
  gap: 6px;
  padding: 2px 2px 4px;
}

.page-title {
  margin: 0;
  font-family: var(--font-family-heading);
  font-size: 24px;
  line-height: 1.2;
  letter-spacing: 0.01em;
  color: #133f8f;
}

.page-subtitle {
  margin: 0;
  font-size: 13px;
  color: #486186;
  line-height: 1.55;
}

@media (max-width: 760px) {
  .page-title {
    font-size: 21px;
  }

  .page-subtitle {
    font-size: 12.5px;
  }
}
</style>




