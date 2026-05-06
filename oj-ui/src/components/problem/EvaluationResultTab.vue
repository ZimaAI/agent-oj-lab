<template>
  <div class="evaluation-result">
    <div v-if="evaluation" class="content">
      <section class="scores-section" aria-label="评分汇总">
        <div class="score-card" :class="getScoreClass(evaluation.correctnessScore)">
          <div class="score-label">正确性</div>
          <div class="score-value">{{ evaluation.correctnessScore }}/10</div>
        </div>
        <div class="score-card" :class="getScoreClass(evaluation.timeComplexityScore)">
          <div class="score-label">时间复杂度</div>
          <div class="score-value">{{ evaluation.timeComplexityScore }}/10</div>
        </div>
        <div class="score-card" :class="getScoreClass(evaluation.spaceComplexityScore)">
          <div class="score-label">空间复杂度</div>
          <div class="score-value">{{ evaluation.spaceComplexityScore }}/10</div>
        </div>
      </section>

      <section class="test-results-section">
        <h3>测试结果</h3>
        <div
          v-for="(result, index) in evaluation.testResults"
          :key="index"
          class="test-result-item"
          :class="{ passed: result.passed, failed: !result.passed }"
        >
          <div class="test-result-header">
            <span class="test-case-name">{{ result.testCase }}</span>
            <span class="test-status">
              {{ result.passed ? '通过' : '失败' }}
            </span>
          </div>
          <div class="test-result-content">
            <div class="test-output">
              <strong>输出：</strong>
              <pre>{{ result.output }}</pre>
            </div>
            <div v-if="result.error" class="test-error">
              <strong>错误：</strong>
              <pre>{{ result.error }}</pre>
            </div>
          </div>
        </div>
      </section>

      <section class="complexity-section">
        <h3>复杂度分析</h3>
        <div class="complexity-item">
          <h4>时间复杂度</h4>
          <p>{{ evaluation.timeComplexityAnalysis }}</p>
        </div>
        <div class="complexity-item">
          <h4>空间复杂度</h4>
          <p>{{ evaluation.spaceComplexityAnalysis }}</p>
        </div>
      </section>

      <section v-if="evaluation.suggestions" class="suggestions-section">
        <h3>优化建议</h3>
        <p>{{ evaluation.suggestions }}</p>
      </section>
    </div>

    <div v-else class="empty-state">
      <p>暂无评价结果</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CodeEvaluation } from '@/types/evaluation'

interface Props {
  evaluation: CodeEvaluation | null
}

defineProps<Props>()

const getScoreClass = (score: number): string => {
  if (score >= 8) return 'score-high'
  if (score >= 5) return 'score-medium'
  return 'score-low'
}
</script>

<style scoped>
.evaluation-result {
  height: 100%;
}

.content {
  padding: 1.2rem;
}

.scores-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.7rem;
  margin-bottom: 1.25rem;
}

.score-card {
  padding: 0.85rem 0.72rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  text-align: center;
  background: var(--color-background-soft);
}

.score-label {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  margin-bottom: 0.2rem;
}

.score-value {
  font-size: 1.3rem;
  font-weight: 700;
}

.score-high {
  border-color: rgba(22, 163, 74, 0.4);
  background: var(--color-success-light);
  color: var(--color-success-dark);
}

.score-medium {
  border-color: rgba(217, 119, 6, 0.45);
  background: rgba(217, 119, 6, 0.14);
  color: #9a4b08;
}

.score-low {
  border-color: rgba(220, 38, 38, 0.45);
  background: var(--color-danger-light);
  color: #b91c1c;
}

.test-results-section,
.complexity-section,
.suggestions-section {
  margin-bottom: 1.2rem;
}

.test-results-section h3,
.complexity-section h3,
.suggestions-section h3 {
  font-size: 1rem;
  font-weight: 700;
  margin-bottom: 0.65rem;
  color: var(--color-text);
}

.test-result-item {
  margin-bottom: 0.64rem;
  padding: 0.78rem;
  border-radius: var(--radius-sm);
  border-left: 4px solid;
}

.test-result-item.passed {
  background: var(--color-success-light);
  border-left-color: var(--color-success);
}

.test-result-item.failed {
  background: var(--color-danger-light);
  border-left-color: var(--color-danger);
}

.test-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  gap: 0.65rem;
}

.test-case-name {
  font-weight: 700;
  font-size: 0.84rem;
}

.test-status {
  font-size: 0.82rem;
  font-weight: 700;
}

.test-result-item.passed .test-status {
  color: var(--color-success-dark);
}

.test-result-item.failed .test-status {
  color: #b91c1c;
}

.test-result-content {
  display: flex;
  flex-direction: column;
  gap: 0.58rem;
}

.test-output,
.test-error {
  font-size: 0.82rem;
}

.test-output pre,
.test-error pre {
  margin: 0.35rem 0 0;
  padding: 0.56rem;
  background-color: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 0.76rem;
}

.complexity-item {
  margin-bottom: 0.62rem;
  padding: 0.8rem;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.complexity-item h4 {
  font-size: 0.84rem;
  font-weight: 700;
  margin: 0 0 0.35rem;
  color: var(--color-text);
}

.complexity-item p {
  margin: 0;
  line-height: 1.62;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.suggestions-section p {
  margin: 0;
  padding: 0.8rem;
  border: 1px solid rgba(217, 119, 6, 0.45);
  background: rgba(217, 119, 6, 0.14);
  border-left: 4px solid var(--color-warning);
  border-radius: var(--radius-sm);
  line-height: 1.62;
  color: #8a4b0f;
  font-size: 0.84rem;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

@media (max-width: 980px) {
  .scores-section {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .content {
    padding: 0.92rem;
  }

  .scores-section {
    grid-template-columns: 1fr;
  }
}
</style>
