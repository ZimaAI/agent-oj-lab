<template>
  <div class="test-case-tab">
    <div v-if="testCase.description" class="test-section">
      <div class="section-header">说明</div>
      <div class="section-content">
        <pre>{{ testCase.description }}</pre>
      </div>
    </div>

    <div class="test-section">
      <div class="section-header">输入</div>
      <div class="section-content">
        <pre>{{ formatTestCaseValue(testCase.input) }}</pre>
      </div>
    </div>

    <div class="test-section">
      <div class="section-header">预期输出</div>
      <div class="section-content">
        <pre>{{ formatTestCaseValue(testCase.expectedOutput) }}</pre>
      </div>
    </div>

    <div v-if="testCase.result" class="test-section">
      <div class="section-header">
        实际输出
        <span :class="['status-badge', testCase.result.passed ? 'passed' : 'failed']">
          {{ testCase.result.passed ? '通过' : '失败' }}
        </span>
      </div>
      <div class="section-content" :class="{ error: !testCase.result.passed }">
        <pre>{{ testCase.result.actualOutput }}</pre>
      </div>
    </div>

    <div v-if="testCase.result && testCase.result.error" class="test-section error-section">
      <div class="section-header">错误信息</div>
      <div class="section-content error">
        <pre>{{ testCase.result.error }}</pre>
      </div>
    </div>

    <div v-if="testCase.result && testCase.result.executionTime !== undefined" class="test-stats">
      <span class="stat-item">
        <span class="stat-label">执行时间:</span>
        <span class="stat-value">{{ testCase.result.executionTime }}ms</span>
      </span>
      <span v-if="testCase.result.memoryUsage !== undefined" class="stat-item">
        <span class="stat-label">内存使用:</span>
        <span class="stat-value">{{ formatMemory(testCase.result.memoryUsage) }}</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { TestCase, TestCaseValue } from '@/types/problem'

interface Props {
  testCase: TestCase
}

const props = defineProps<Props>()

const formatTestCaseValue = (value: TestCaseValue) => {
  if (typeof value === 'string') {
    return value
  }

  try {
    return JSON.stringify(value, null, 2)
  } catch (e) {
    console.error('Failed to stringify test case value:', e)
    return String(value)
  }
}

const formatMemory = (bytes: number): string => {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)}KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)}MB`
}
</script>

<style scoped>
.test-case-tab {
  padding: 16px;
}

.test-section {
  margin-bottom: 16px;
}

.test-section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.section-content {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-background-soft);
}

.section-content pre {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.section-content.error {
  border-color: var(--color-danger);
  background: var(--color-danger-light);
}

.section-content.error pre {
  color: var(--color-danger);
}

.error-section .section-header {
  color: var(--color-danger);
}

.status-badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.passed {
  background: var(--color-success-light);
  color: var(--color-success);
}

.status-badge.failed {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.test-stats {
  display: flex;
  gap: 16px;
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--color-background-mute);
  font-size: 13px;
}

.stat-item {
  display: flex;
  gap: 4px;
}

.stat-label {
  color: var(--color-text-secondary);
}

.stat-value {
  color: var(--color-text);
  font-weight: 500;
}
</style>