<template>
  <div class="problem-panel">
    <div class="panel-header">
      <div class="tabs" role="tablist" aria-label="题目信息面板">
        <button
          id="problem-tab-description"
          class="tab"
          role="tab"
          :aria-selected="activeTab === 'description'"
          aria-controls="problem-panel-description"
          :class="{ active: activeTab === 'description' }"
          @click="setActiveTab('description')"
        >
          题目描述
        </button>
        <button
          id="problem-tab-submissions"
          class="tab"
          role="tab"
          :aria-selected="activeTab === 'submissions'"
          aria-controls="problem-panel-submissions"
          :class="{ active: activeTab === 'submissions' }"
          @click="setActiveTab('submissions')"
        >
          提交记录
        </button>
        <button
          id="problem-tab-evaluation"
          class="tab"
          role="tab"
          :aria-selected="activeTab === 'evaluation'"
          aria-controls="problem-panel-evaluation"
          :class="{ active: activeTab === 'evaluation' }"
          @click="setActiveTab('evaluation')"
        >
          评价结果
        </button>
      </div>
    </div>

    <div class="panel-content">
      <div
        v-if="activeTab === 'description'"
        id="problem-panel-description"
        role="tabpanel"
        aria-labelledby="problem-tab-description"
        class="panel-scroll"
      >
        <ProblemDescription :problem="problem" />
      </div>
      <div
        v-else-if="activeTab === 'submissions'"
        id="problem-panel-submissions"
        role="tabpanel"
        aria-labelledby="problem-tab-submissions"
        class="panel-scroll"
      >
        <SubmissionList :problem-id="currentQuestionId" />
      </div>
      <div
        v-else-if="activeTab === 'evaluation'"
        id="problem-panel-evaluation"
        role="tabpanel"
        aria-labelledby="problem-tab-evaluation"
        class="panel-scroll"
      >
        <EvaluationResultTab :evaluation="evaluationResult ?? null" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AlgorithmQuestion } from '@/types/problem'
import type { CodeEvaluation } from '@/types/evaluation'
import ProblemDescription from './ProblemDescription.vue'
import SubmissionList from './SubmissionList.vue'
import EvaluationResultTab from './EvaluationResultTab.vue'

export type ProblemTab = 'description' | 'submissions' | 'evaluation'

interface Props {
  problem: AlgorithmQuestion | null
  currentQuestionId?: number
  evaluationResult?: CodeEvaluation | null
  activeTab?: ProblemTab
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:activeTab', value: ProblemTab): void
}>()

const activeTab = ref<ProblemTab>(props.activeTab ?? 'description')

const setActiveTab = (value: ProblemTab) => {
  activeTab.value = value
}

watch(
  () => props.activeTab,
  (newValue) => {
    if (newValue && newValue !== activeTab.value) {
      activeTab.value = newValue
    }
  }
)

watch(activeTab, (newValue) => {
  emit('update:activeTab', newValue)
})

watch(
  () => props.evaluationResult,
  (newValue) => {
    if (newValue) {
      activeTab.value = 'evaluation'
    }
  }
)
</script>

<style scoped>
.problem-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.panel-header {
  border-bottom: 1px solid var(--color-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent), var(--color-background);
  padding: 0.58rem 0.68rem;
}

.tabs {
  display: flex;
  gap: 0.45rem;
  overflow-x: auto;
}

.tab {
  min-height: 40px;
  padding: 0 0.92rem;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  white-space: nowrap;
  transition: background-color 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.tab:hover {
  color: var(--color-text);
  border-color: var(--color-border);
  background: var(--color-background-soft);
  transform: translateY(-1px);
}

.tab:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.tab.active {
  color: #fff;
  border-color: var(--color-primary);
  background: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.panel-content {
  flex: 1;
  min-height: 0;
  background: var(--color-background);
}

.panel-scroll {
  height: 100%;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .tab {
    min-height: 38px;
    padding: 0 0.78rem;
  }
}
</style>
