<template>
  <div class="test-case-panel">
    <div class="test-case-tabs">
      <div class="tab-list" role="tablist" aria-label="测试用例标签">
        <button
          v-for="(testCase, index) in testCases"
          :key="index"
          :class="['tab', { active: activeTab === index }]"
          role="tab"
          :aria-selected="activeTab === index"
          :aria-controls="`testcase-panel-${index}`"
          :id="`testcase-tab-${index}`"
          :tabindex="activeTab === index ? 0 : -1"
          @click="activeTab = index"
          @keydown.left.prevent="handleTabKeydown(-1)"
          @keydown.right.prevent="handleTabKeydown(1)"
        >
          测试用例 {{ index + 1 }}
          <span v-if="loading" class="result-badge loading-badge">…</span>
          <span v-else-if="testCase.result" :class="['result-badge', testCase.result.passed ? 'passed' : 'failed']">
            {{ testCase.result.passed ? '✓' : '✗' }}
          </span>
        </button>
      </div>
      <div
        v-if="resultSummary.hasResult"
        class="tab-summary"
        :class="{ 'all-passed': resultSummary.failed === 0 }"
      >
        通过 {{ resultSummary.passed }}/{{ resultSummary.total }}
        <span v-if="resultSummary.failed > 0"> | 失败 {{ resultSummary.failed }}</span>
        <span v-else-if="resultSummary.pending > 0"> | 待运行 {{ resultSummary.pending }}</span>
      </div>
    </div>
    <div class="test-case-content">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <span>运行中...</span>
      </div>
      <TestCaseTab
        v-else-if="testCases.length > 0 && activeTab < testCases.length"
        :id="`testcase-panel-${activeTab}`"
        role="tabpanel"
        :aria-labelledby="`testcase-tab-${activeTab}`"
        :test-case="testCases[activeTab]!"
      />
      <div v-else class="empty-state">
        <p>暂无测试用例</p>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import type { TestCase } from '@/types/problem'

export interface TestCaseResultSummary {
  total: number
  passed: number
  failed: number
  pending: number
  hasResult: boolean
}

export function resolveFirstFailedTabIndex(testCases: TestCase[]): number | null {
  const failedIndex = testCases.findIndex((testCase) => testCase.result && !testCase.result.passed)
  return failedIndex >= 0 ? failedIndex : null
}

export function resolveNextActiveTab(testCases: TestCase[], currentActiveTab: number): number {
  if (!testCases.length) {
    return 0
  }

  const failedIndex = resolveFirstFailedTabIndex(testCases)
  if (failedIndex !== null) {
    return failedIndex
  }

  if (currentActiveTab < 0) {
    return 0
  }
  if (currentActiveTab >= testCases.length) {
    return testCases.length - 1
  }
  return currentActiveTab
}

export function summarizeTestCaseResults(testCases: TestCase[]): TestCaseResultSummary {
  let passed = 0
  let failed = 0
  let pending = 0

  for (const testCase of testCases) {
    if (!testCase.result) {
      pending += 1
      continue
    }

    if (testCase.result.passed) {
      passed += 1
      continue
    }

    failed += 1
  }

  return {
    total: testCases.length,
    passed,
    failed,
    pending,
    hasResult: passed + failed > 0,
  }
}
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import TestCaseTab from './TestCaseTab.vue'
import type { TestCase as PanelTestCase } from '@/types/problem'

interface Props {
  testCases?: PanelTestCase[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  testCases: () => [],
  loading: false,
})

const activeTab = ref(0)
const resultSummary = computed(() => summarizeTestCaseResults(props.testCases ?? []))

// 使用左右方向键切换测试用例标签，提升键盘操作效率。
const handleTabKeydown = (offset: number) => {
  if (!props.testCases.length) {
    return
  }
  const total = props.testCases.length
  activeTab.value = (activeTab.value + offset + total) % total
}

watch(
  () => props.testCases,
  (nextTestCases) => {
    activeTab.value = resolveNextActiveTab(nextTestCases ?? [], activeTab.value)
  },
  { immediate: true }
)
</script>

<style scoped>
.test-case-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-background);
}

.test-case-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-background-soft);
}

.tab-list {
  display: flex;
  gap: 4px;
  flex: 1;
  overflow-x: auto;
  min-width: 0;
}

.tab-summary {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-background);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.2;
}

.tab-summary.all-passed {
  border-color: var(--color-success);
  color: var(--color-success);
}

.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  border-radius: 4px 4px 0 0;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}

.tab:hover {
  background: var(--color-background-mute);
  color: var(--color-text);
}

.tab.active {
  background: var(--color-background);
  color: var(--color-primary);
  font-weight: 500;
}

.tab:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.result-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: bold;
}

.result-badge.passed {
  background: var(--color-success-light);
  color: var(--color-success);
}

.result-badge.failed {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.test-case-content {
  flex: 1;
  overflow: auto;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-badge {
  background: var(--color-background-mute);
  color: var(--color-text-secondary);
  font-size: 10px;
}
</style>
