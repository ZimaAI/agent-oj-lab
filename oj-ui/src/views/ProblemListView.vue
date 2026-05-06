<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useProblemList, type ProblemFilterInput } from '@/composables/useProblemList'
import ProblemFilters from '@/components/problem/ProblemFilters.vue'
import ProblemTable from '@/components/problem/ProblemTable.vue'

const {
  problems,
  tags,
  loading,
  error,
  pagination,
  loadProblems,
  loadTags,
  changePage,
  applyFilters,
  resetFilters,
} = useProblemList()

onMounted(async () => {
  await loadTags()
  await loadProblems()
})

const visiblePages = computed<(number | '...')[]>(() => {
  const total = pagination.pages
  const current = pagination.current
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1)
  }

  const pages: (number | '...')[] = [1]
  if (current > 3) {
    pages.push('...')
  }

  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  for (let page = start; page <= end; page += 1) {
    pages.push(page)
  }

  if (current < total - 2) {
    pages.push('...')
  }

  pages.push(total)
  return pages
})

function handleFilter(filters: ProblemFilterInput) {
  applyFilters(filters)
}

function handleReset() {
  resetFilters()
}

function handlePageChange(page: number) {
  if (page !== pagination.current) {
    changePage(page)
  }
}
</script>

<template>
  <div class="problem-list-view">
    <div class="container">
      <header class="page-header">
        <h1 class="page-title">算法题库</h1>
        <p class="page-subtitle">按难度和标签筛选后即可开始训练</p>
      </header>

      <ProblemFilters
        :tags="tags"
        :loading="loading"
        @filter="handleFilter"
        @reset="handleReset"
      />

      <div v-if="error && !loading" class="error-state" role="alert">
        <span class="error-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" />
            <path d="M12 8V13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <circle cx="12" cy="16" r="1" fill="currentColor" />
          </svg>
        </span>
        <p class="error-text">{{ error }}</p>
        <button class="retry-button" @click="loadProblems">
          重试
        </button>
      </div>

      <ProblemTable
        v-else
        :problems="problems"
        :loading="loading"
      />

      <nav v-if="pagination.pages > 1" class="pagination" aria-label="题库分页">
        <button
          class="pagination-btn"
          :disabled="pagination.current === 1 || loading"
          aria-label="上一页"
          @click="handlePageChange(pagination.current - 1)"
        >
          上一页
        </button>

        <div class="pagination-pages">
          <button
            v-for="(page, index) in visiblePages"
            :key="`${page}-${index}`"
            class="pagination-page"
            :class="{ active: page === pagination.current, ellipsis: page === '...' }"
            :disabled="loading || page === '...'"
            :aria-label="page === '...' ? '省略页码' : `第 ${page} 页`"
            @click="typeof page === 'number' && handlePageChange(page)"
          >
            {{ page }}
          </button>
        </div>

        <button
          class="pagination-btn"
          :disabled="pagination.current === pagination.pages || loading"
          aria-label="下一页"
          @click="handlePageChange(pagination.current + 1)"
        >
          下一页
        </button>
      </nav>
    </div>
  </div>
</template>

<style scoped>
.problem-list-view {
  min-height: 100%;
  padding: 1.65rem 0 2.2rem;
  background: var(--color-background);
}

.container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 1.2rem;
}

.page-header {
  margin-bottom: 1rem;
}

.page-title {
  display: inline-flex;
  align-items: center;
  position: relative;
  font-size: 1.8rem;
  font-weight: 700;
  font-family: var(--font-heading);
  letter-spacing: 0.01em;
  color: var(--color-text);
}

.page-title::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -9px;
  width: 68px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-primary), transparent);
}

.page-subtitle {
  margin-top: 0.8rem;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.62rem;
  margin-top: 1.4rem;
  flex-wrap: wrap;
}

.pagination-btn {
  min-height: 40px;
  padding: 0 0.95rem;
  border: 1px solid var(--color-border);
  background: var(--color-background-soft);
  color: var(--color-text);
  font-size: 0.84rem;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease;
  border-radius: var(--radius-sm);
}

.pagination-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-dim);
  transform: translateY(-1px);
}

.pagination-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.pagination-btn:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.pagination-pages {
  display: flex;
  gap: 0.46rem;
  flex-wrap: wrap;
}

.pagination-page {
  min-width: 40px;
  min-height: 40px;
  padding: 0 0.55rem;
  border: 1px solid var(--color-border);
  background: var(--color-background-soft);
  color: var(--color-text);
  font-size: 0.84rem;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease, box-shadow 0.15s ease;
  border-radius: var(--radius-sm);
}

.pagination-page:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-dim);
  transform: translateY(-1px);
}

.pagination-page:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.pagination-page.active {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.pagination-page.ellipsis {
  cursor: default;
  color: var(--color-text-secondary);
}

.pagination-page:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2.3rem 2rem;
  text-align: center;
  margin-top: 1.2rem;
  border: 1px solid rgba(220, 38, 38, 0.4);
  border-radius: var(--radius-md);
  background: var(--color-danger-light);
}

.error-icon {
  width: 2.3rem;
  height: 2.3rem;
  color: var(--color-danger);
  margin-bottom: 0.55rem;
}

.error-icon svg {
  width: 100%;
  height: 100%;
}

.error-text {
  font-size: 0.9rem;
  color: #b91c1c;
  margin-bottom: 0.85rem;
}

.retry-button {
  min-height: 38px;
  padding: 0 1.2rem;
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, background-color 0.15s ease, border-color 0.15s ease;
  border-radius: var(--radius-sm);
}

.retry-button:hover {
  background: var(--color-primary-dark);
  border-color: var(--color-primary-dark);
  transform: translateY(-1px);
}

.retry-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

@media (max-width: 768px) {
  .problem-list-view {
    padding-top: 1.1rem;
  }

  .container {
    padding: 0 0.85rem;
  }

  .page-title {
    font-size: 1.52rem;
  }
}
</style>
