<script lang="ts">
import type { Difficulty } from '@/types/problem'

const difficultyLabels: Record<Difficulty, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
}

export function getDifficultyLabel(difficulty: Difficulty | null | undefined): string {
  if (!difficulty) {
    return '未知'
  }
  return difficultyLabels[difficulty] || '未知'
}

export function getDifficultyClass(difficulty: Difficulty | null | undefined): string {
  if (!difficulty) {
    return 'difficulty-unknown'
  }
  return `difficulty-${difficulty.toLowerCase()}`
}
</script>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { AlgorithmQuestion } from '@/types/problem'

interface Props {
  problems: AlgorithmQuestion[]
  loading?: boolean
}

defineProps<Props>()
const router = useRouter()

function handleRowClick(problemId: number) {
  router.push(`/problems/${problemId}`)
}
</script>

<template>
  <div class="problem-table-container">
    <div v-if="!loading && problems.length === 0" class="empty-state">
      <span class="empty-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="4" y="3" width="16" height="18" rx="2" stroke="currentColor" stroke-width="1.8" />
          <path d="M8 8H16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          <path d="M8 12H16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          <path d="M8 16H13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
      </span>
      <p class="empty-text">暂无题目</p>
      <p class="empty-hint">请尝试调整筛选条件</p>
    </div>

    <div v-else class="table-scroll">
      <table class="problem-table">
        <thead>
          <tr>
            <th class="col-id">题号</th>
            <th class="col-title">题目</th>
            <th class="col-difficulty">难度</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-if="loading"
            v-for="i in 8"
            :key="`skeleton-${i}`"
            class="skeleton-row"
            aria-hidden="true"
          >
            <td class="col-id">
              <div class="skeleton skeleton-text"></div>
            </td>
            <td class="col-title">
              <div class="skeleton skeleton-text skeleton-title"></div>
            </td>
            <td class="col-difficulty">
              <div class="skeleton skeleton-badge"></div>
            </td>
          </tr>

          <tr
            v-if="!loading"
            v-for="problem in problems"
            :key="problem.id"
            role="button"
            tabindex="0"
            :aria-label="`题目 ${problem.id}: ${problem.title}, 难度: ${getDifficultyLabel(problem.difficulty)}`"
            @click="handleRowClick(problem.id)"
            @keydown.enter="handleRowClick(problem.id)"
            @keydown.space.prevent="handleRowClick(problem.id)"
          >
            <td class="col-id">{{ problem.id }}</td>
            <td class="col-title">{{ problem.title }}</td>
            <td class="col-difficulty">
              <span
                class="difficulty-badge"
                :class="getDifficultyClass(problem.difficulty)"
              >
                {{ getDifficultyLabel(problem.difficulty) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.problem-table-container {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  overflow: hidden;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.table-scroll {
  overflow-x: auto;
}

.problem-table {
  width: 100%;
  min-width: 560px;
  border-collapse: collapse;
  table-layout: fixed;
}

.problem-table thead {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent), var(--color-background-mute);
}

.problem-table th {
  padding: 0.95rem 1rem;
  text-align: left;
  font-weight: 700;
  color: var(--color-text);
  font-size: 0.8rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border-bottom: 1px solid var(--color-border);
}

.problem-table tbody tr {
  border-bottom: 1px solid var(--color-border);
  transition: background-color 0.15s ease, box-shadow 0.15s ease;
  cursor: pointer;
}

.problem-table tbody tr:last-child {
  border-bottom: none;
}

.problem-table tbody tr:hover {
  background: var(--color-background-mute);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.problem-table tbody tr:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
  background: var(--color-primary-dim);
}

.problem-table td {
  padding: 0.9rem 1rem;
  color: var(--color-text);
  font-size: 0.875rem;
  vertical-align: middle;
}

.col-id {
  width: 10%;
  color: var(--color-text-secondary);
  font-weight: 600;
}

.col-title {
  width: 70%;
}

.col-difficulty {
  width: 20%;
}

.difficulty-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 60px;
  min-height: 26px;
  padding: 0 0.7rem;
  font-size: 0.74rem;
  font-weight: 700;
  border-radius: 999px;
  border: 1px solid;
}

.difficulty-easy {
  background: var(--color-success-light);
  color: var(--color-success-dark);
  border-color: rgba(22, 163, 74, 0.48);
}

.difficulty-medium {
  background: rgba(217, 119, 6, 0.14);
  color: #9a4b08;
  border-color: rgba(217, 119, 6, 0.48);
}

.difficulty-hard {
  background: var(--color-danger-light);
  color: #b91c1c;
  border-color: rgba(220, 38, 38, 0.48);
}

.difficulty-unknown {
  background: var(--color-background-soft);
  color: var(--color-text-secondary);
  border-color: var(--color-border);
}

.skeleton-row {
  pointer-events: none;
}

.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-background-soft) 25%,
    var(--color-background-mute) 50%,
    var(--color-background-soft) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-loading 1.25s ease-in-out infinite;
  border-radius: var(--radius-sm);
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.skeleton-text {
  height: 1rem;
  width: 3rem;
}

.skeleton-title {
  width: 14rem;
}

.skeleton-badge {
  height: 1.4rem;
  width: 4rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3.2rem 2rem;
  text-align: center;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent);
}

.empty-icon {
  width: 2.3rem;
  height: 2.3rem;
  margin-bottom: 0.75rem;
  color: var(--color-text-secondary);
  opacity: 0.75;
}

.empty-icon svg {
  width: 100%;
  height: 100%;
}

.empty-text {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 0.35rem;
}

.empty-hint {
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .problem-table {
    min-width: 520px;
  }

  .problem-table th,
  .problem-table td {
    padding: 0.76rem 0.62rem;
  }
}
</style>
