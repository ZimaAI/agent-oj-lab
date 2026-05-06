<template>
  <div class="problem-description">
    <div v-if="problem" class="content">
      <div class="problem-header">
        <h1 class="problem-title">{{ problem.title }}</h1>
        <div class="problem-meta">
          <span class="difficulty-badge" :class="difficultyClass">
            {{ difficultyText }}
          </span>
          <div class="tags" role="list" aria-label="题目标签">
            <button
              v-for="(tag, index) in problem.tags"
              :key="`${tag.id}-${tag.tagName}-${index}`"
              type="button"
              class="tag-chip"
              @click="handleTagClick(tag.id)"
            >
              {{ tag.tagName }}
            </button>
          </div>
        </div>
      </div>

      <div class="problem-content">
        <section class="description-section">
          <h3>题目描述</h3>
          <div class="description-text">{{ problem.description }}</div>
        </section>

        <section v-if="displayCases.length > 0" class="test-cases-section">
          <h3>示例用例</h3>
          <article
            v-for="(testCase, index) in displayCases"
            :key="index"
            class="test-case"
          >
            <h4>测试用例 {{ index + 1 }}</h4>
            <div class="test-case-content">
              <p v-if="testCase.description" class="test-description">{{ testCase.description }}</p>
              <div class="test-input">
                <strong>输入</strong>
                <pre>{{ testCase.stdin }}</pre>
              </div>
              <div class="test-output">
                <strong>预期输出</strong>
                <pre>{{ testCase.expectedStdout }}</pre>
              </div>
            </div>
          </article>
        </section>
      </div>
    </div>

    <div v-else class="empty-state">
      <p>请选择一个题目</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { AlgorithmQuestion, StandardCase, TestCaseValue } from '@/types/problem'
import { Difficulty } from '@/types/problem'

interface Props {
  problem: AlgorithmQuestion | null
}

const props = defineProps<Props>()
const router = useRouter()
const displayCases = computed<StandardCase[]>(() => {
  if (!props.problem) {
    return []
  }
  if (props.problem.standardCasePool && props.problem.standardCasePool.length > 0) {
    return props.problem.standardCasePool.filter((item) => item.publicCase)
  }
  const legacyCases = props.problem.sharedTestCases ?? props.problem.testCases ?? []
  return legacyCases.map((item) => ({
    stdin: formatTestCaseValue(item.input),
    expectedStdout: formatTestCaseValue(item.expectedOutput),
    publicCase: true,
    description: item.description ?? null,
  }))
})

const difficultyClass = computed(() => {
  if (!props.problem || !props.problem.difficulty) return ''
  return `difficulty-${props.problem.difficulty.toLowerCase()}`
})

const difficultyText = computed(() => {
  if (!props.problem || !props.problem.difficulty) return ''
  const map: Record<Difficulty, string> = {
    [Difficulty.EASY]: '简单',
    [Difficulty.MEDIUM]: '中等',
    [Difficulty.HARD]: '困难',
  }
  return map[props.problem.difficulty] || props.problem.difficulty
})

const formatTestCaseValue = (value: TestCaseValue) => {
  if (typeof value === 'string') {
    return value
  }

  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

const handleTagClick = (tagId: number) => {
  router.push({ path: '/problems', query: { tagIds: String(tagId) } })
}
</script>

<style scoped>
.problem-description {
  height: 100%;
}

.content {
  padding: 1.2rem;
}

.problem-header {
  margin-bottom: 1.2rem;
}

.problem-title {
  font-size: 1.35rem;
  font-weight: 700;
  margin: 0;
  color: var(--color-text);
  line-height: 1.35;
}

.problem-meta {
  display: flex;
  align-items: flex-start;
  gap: 0.68rem;
  margin-top: 0.75rem;
}

.difficulty-badge {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 0.8rem;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 700;
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
  border-color: rgba(220, 38, 38, 0.45);
}

.tags {
  display: flex;
  gap: 0.52rem;
  flex-wrap: wrap;
}

.tag-chip {
  min-height: 30px;
  padding: 0 0.75rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-background-soft);
  font-size: 0.76rem;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color 0.18s ease, color 0.18s ease, background-color 0.18s ease;
}

.tag-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-dim);
}

.tag-chip:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.problem-content {
  color: var(--color-text);
}

.description-section,
.test-cases-section {
  margin-bottom: 1.5rem;
}

.description-section h3,
.test-cases-section h3 {
  font-size: 1rem;
  font-weight: 700;
  margin: 0 0 0.72rem;
  color: var(--color-text);
}

.description-text {
  line-height: 1.74;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
}

.test-case {
  margin-bottom: 0.92rem;
  padding: 0.88rem;
  background-color: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.test-case h4 {
  font-size: 0.85rem;
  font-weight: 700;
  margin: 0 0 0.62rem;
  color: var(--color-text);
}

.test-case-content {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.test-description {
  margin: 0;
  font-size: 0.84rem;
  color: var(--color-text-secondary);
}

.test-input,
.test-output {
  font-size: 0.84rem;
}

.test-input pre,
.test-output pre {
  margin: 0.35rem 0 0;
  padding: 0.6rem 0.65rem;
  background-color: var(--color-background-mute);
  border-radius: 8px;
  border: 1px solid var(--color-border);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
  font-size: 0.92rem;
}

@media (max-width: 768px) {
  .content {
    padding: 0.92rem;
  }

  .problem-title {
    font-size: 1.15rem;
  }

  .problem-meta {
    flex-direction: column;
  }
}
</style>
