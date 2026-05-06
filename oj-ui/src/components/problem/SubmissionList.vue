<template>
  <div class="submission-list">
    <div v-if="loading && submissions.length === 0" class="loading-state" aria-live="polite">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="error" class="error-state" role="alert">
      <p>{{ error }}</p>
      <button @click="loadSubmissions()" class="retry-button">重试</button>
    </div>

    <div v-else-if="submissions.length === 0" class="empty-state">
      <p>暂无提交记录，提交代码后可在此查看</p>
    </div>

    <div v-else class="list-content">
      <div class="list-wrapper" :class="{ 'loading-overlay': loading }">
        <article
          v-for="submission in submissions"
          :key="submission.id"
          class="submission-item"
          :class="{ expanded: expandedId === submission.id }"
        >
          <button
            type="button"
            class="item-header"
            :aria-expanded="expandedId === submission.id"
            :aria-controls="`submission-detail-${submission.id}`"
            @click="toggleExpand(submission.id)"
          >
            <div class="header-left">
              <span class="submission-id">#{{ submission.id }}</span>
              <span class="submission-time">{{ formatTime(submission.createTime) }}</span>
            </div>
            <div class="header-right">
              <span class="language-badge">{{ submission.language }}</span>
              <span class="test-result">
                通过 {{ submission.passCount }}/{{ submission.totalCount }}
              </span>
              <span class="expand-icon" aria-hidden="true">{{ expandedId === submission.id ? '收起' : '展开' }}</span>
            </div>
          </button>

          <div
            v-if="expandedId === submission.id"
            :id="`submission-detail-${submission.id}`"
            class="item-detail"
          >
            <div v-if="detailLoading" class="detail-loading">
              <div class="spinner-small"></div>
              <span>加载详情中...</span>
            </div>
            <div v-else-if="detailData" class="detail-content">
              <section class="detail-section">
                <h4>提交代码</h4>
                <pre class="code-block"><code>{{ detailData.code }}</code></pre>
              </section>

              <section v-if="detailData.testResults" class="detail-section">
                <h4>测试结果</h4>
                <div class="test-results">
                  <div
                    v-for="(result, index) in detailData.testResults"
                    :key="index"
                    class="test-case"
                    :class="{ passed: result.success, failed: !result.success }"
                  >
                    <span class="test-icon" aria-hidden="true">{{ result.success ? '✓' : '✗' }}</span>
                    <span class="test-label">测试用例 {{ index + 1 }}</span>
                    <span v-if="!result.success && result.errorMessage" class="test-error">
                      {{ result.errorMessage }}
                    </span>
                  </div>
                </div>
              </section>

              <section v-if="detailData.codeEvaluation" class="detail-section">
                <h4>代码评价</h4>
                <div class="evaluation-content">
                  {{ formatCodeEvaluation(detailData.codeEvaluation) }}
                </div>
              </section>
            </div>
            <div v-else class="detail-error">
              <p>加载详情失败，请重试</p>
            </div>
          </div>
        </article>
      </div>

      <div v-if="total > 0" class="pagination" aria-label="提交记录分页">
        <button
          class="page-btn"
          :disabled="currentPage === 1"
          aria-label="上一页"
          @click="loadSubmissions(currentPage - 1)"
        >
          ‹
        </button>
        <button
          v-for="page in visiblePages"
          :key="page"
          class="page-btn"
          :class="{ active: page === currentPage }"
          :aria-label="`第 ${page} 页`"
          @click="loadSubmissions(page)"
        >
          {{ page }}
        </button>
        <button
          class="page-btn"
          :disabled="currentPage === totalPages"
          aria-label="下一页"
          @click="loadSubmissions(currentPage + 1)"
        >
          ›
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue'
import { useSubmissionList } from '@/composables/useSubmissionList'
import type { CodeSubmissionEvaluation } from '@/types/problem'

interface Props {
  problemId?: number
}

const props = defineProps<Props>()

const problemIdRef = toRef(props, 'problemId')

const {
  submissions,
  loading,
  error,
  total,
  currentPage,
  pageSize,
  expandedId,
  detailLoading,
  detailData,
  loadSubmissions,
  toggleExpand,
} = useSubmissionList(problemIdRef)

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const visiblePages = computed(() => {
  const pages: number[] = []
  const maxVisible = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxVisible / 2))
  let end = Math.min(totalPages.value, start + maxVisible - 1)

  if (end - start < maxVisible - 1) {
    start = Math.max(1, end - maxVisible + 1)
  }

  for (let i = start; i <= end; i++) {
    pages.push(i)
  }

  return pages
})

const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const formatCodeEvaluation = (evaluation: CodeSubmissionEvaluation | null) => {
  return evaluation ?? ''
}
</script>

<style scoped>
.submission-list {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.loading-state,
.error-state,
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: 0.88rem;
}

.spinner {
  width: 34px;
  height: 34px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 0.8rem;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.retry-button {
  margin-top: 0.7rem;
  min-height: 38px;
  padding: 0 0.9rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s ease, transform 0.15s ease;
}

.retry-button:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.retry-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.list-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 0.9rem;
  position: relative;
}

.list-wrapper.loading-overlay::after {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(5, 16, 31, 0.08);
  pointer-events: none;
}

.submission-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  margin-bottom: 0.72rem;
  overflow: hidden;
  background: var(--color-background-soft);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.submission-item:hover,
.submission-item.expanded {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.item-header {
  width: 100%;
  padding: 0.68rem 0.82rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: none;
  background: transparent;
  cursor: pointer;
  user-select: none;
  text-align: left;
}

.item-header:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--primary-ring);
}

.header-left {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  min-width: 0;
}

.submission-id {
  font-weight: 700;
  color: var(--color-text);
  font-size: 0.86rem;
}

.submission-time {
  color: var(--color-text-secondary);
  font-size: 0.77rem;
}

.header-right {
  display: flex;
  gap: 0.58rem;
  align-items: center;
  font-size: 0.8rem;
}

.language-badge {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 0.48rem;
  border-radius: 999px;
  background: var(--color-background-mute);
  color: var(--color-text-secondary);
}

.test-result {
  font-weight: 600;
  color: var(--color-text);
}

.expand-icon {
  color: var(--color-primary);
  font-size: 0.74rem;
}

.item-detail {
  border-top: 1px solid var(--color-border);
  padding: 0.86rem;
  background: var(--color-background-mute);
}

.detail-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.8rem;
  color: var(--color-text-secondary);
}

.spinner-small {
  width: 18px;
  height: 18px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 0.92rem;
}

.detail-section h4 {
  font-size: 0.84rem;
  font-weight: 700;
  margin: 0 0 0.45rem;
  color: var(--color-text);
}

.code-block {
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.7rem;
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 0.76rem;
  line-height: 1.56;
}

.test-results {
  display: flex;
  flex-direction: column;
  gap: 0.44rem;
}

.test-case {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.46rem 0.62rem;
  border-radius: 8px;
  border: 1px solid;
  font-size: 0.8rem;
}

.test-case.passed {
  background: var(--color-success-light);
  border-color: rgba(22, 163, 74, 0.4);
  color: var(--color-success-dark);
}

.test-case.failed {
  background: var(--color-danger-light);
  border-color: rgba(220, 38, 38, 0.42);
  color: #b91c1c;
}

.test-icon {
  font-weight: 700;
}

.test-label {
  font-weight: 600;
}

.test-error {
  margin-left: auto;
  font-size: 0.74rem;
}

.evaluation-content {
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.72rem;
  white-space: pre-wrap;
  font-size: 0.8rem;
  line-height: 1.6;
}

.detail-error {
  text-align: center;
  padding: 0.9rem;
  color: var(--color-text-secondary);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.38rem;
  padding: 0.74rem;
  border-top: 1px solid var(--color-border);
}

.page-btn {
  min-width: 38px;
  min-height: 38px;
  padding: 0 0.5rem;
  border: 1px solid var(--color-border);
  background: var(--color-background);
  color: var(--color-text);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.82rem;
  transition: border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.page-btn.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.page-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.page-btn:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

@media (max-width: 820px) {
  .item-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
