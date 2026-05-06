<script lang="ts">
export const TAG_LIST_COLLAPSED_CLASS = 'tag-list-collapsed'
export const TAG_LIST_EXPANDED_CLASS = 'tag-list-expanded'

// 根据折叠状态返回标签容器样式类。
export function resolveTagListClassName(isExpanded: boolean): string {
  return isExpanded ? TAG_LIST_EXPANDED_CLASS : TAG_LIST_COLLAPSED_CLASS
}

// 根据折叠状态返回切换按钮文案。
export function resolveTagCollapseToggleText(isExpanded: boolean): string {
  return isExpanded ? '收起标签' : '展开标签'
}
</script>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AlgorithmQuestionType, Difficulty, Tag } from '@/types/problem'

interface Props {
  tags: Tag[]
  loading?: boolean
}

interface Emits {
  (e: 'filter', filters: { difficulty?: Difficulty; type?: AlgorithmQuestionType; tagIds: number[] }): void
  (e: 'reset'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const selectedDifficulty = ref<Difficulty | ''>('')
const selectedType = ref<AlgorithmQuestionType>('AI')
const selectedTagIds = ref<number[]>([])
const isTagListExpanded = ref(false)
const tagListId = 'problem-filter-tag-list'
const showTagToggleButton = computed(() => props.tags.length > 0)
const tagListClassName = computed(() => resolveTagListClassName(isTagListExpanded.value))
const tagCollapseToggleText = computed(() => resolveTagCollapseToggleText(isTagListExpanded.value))

function handleDifficultyChange() {
  emitFilter()
}

function handleTypeChange() {
  emitFilter()
}

function handleTagChange(tagId: number) {
  const index = selectedTagIds.value.indexOf(tagId)
  if (index > -1) {
    selectedTagIds.value.splice(index, 1)
  } else {
    selectedTagIds.value.push(tagId)
  }
  emitFilter()
}

function emitFilter() {
  emit('filter', {
    difficulty: selectedDifficulty.value || undefined,
    type: selectedType.value,
    tagIds: selectedTagIds.value,
  })
}

// 切换标签区域折叠与展开状态。
function toggleTagListExpanded() {
  isTagListExpanded.value = !isTagListExpanded.value
}

function handleReset() {
  selectedDifficulty.value = ''
  selectedType.value = 'AI'
  selectedTagIds.value = []
  isTagListExpanded.value = false
  emit('reset')
}
</script>

<template>
  <div class="problem-filters" aria-label="题库筛选条件">
    <div class="filters-grid">
      <div class="filter-section">
        <label class="filter-label" for="source-select">来源</label>
        <select
          id="source-select"
          v-model="selectedType"
          class="filter-select"
          :disabled="loading"
          @change="handleTypeChange"
        >
          <option value="AI">AI</option>
          <option value="SYSTEM">系统题</option>
        </select>
      </div>

      <div class="filter-section">
        <label class="filter-label" for="difficulty-select">难度</label>
        <select
          id="difficulty-select"
          v-model="selectedDifficulty"
          class="filter-select"
          :disabled="loading"
          @change="handleDifficultyChange"
        >
          <option value="">全部难度</option>
          <option value="EASY">简单</option>
          <option value="MEDIUM">中等</option>
          <option value="HARD">困难</option>
        </select>
      </div>

      <fieldset class="filter-section tag-section">
        <legend class="filter-label tag-legend">
          <span>标签</span>
          <button
            v-if="showTagToggleButton"
            type="button"
            class="tag-toggle-button"
            :disabled="loading"
            :aria-expanded="isTagListExpanded"
            :aria-controls="tagListId"
            @click="toggleTagListExpanded"
          >
            {{ tagCollapseToggleText }}
          </button>
        </legend>
        <div :id="tagListId" class="tag-list" :class="tagListClassName">
          <label
            v-for="tag in tags"
            :key="tag.id"
            class="tag-checkbox"
            :for="`tag-${tag.id}`"
          >
            <input
              :id="`tag-${tag.id}`"
              type="checkbox"
              :value="tag.id"
              :checked="selectedTagIds.includes(tag.id)"
              :disabled="loading"
              @change="handleTagChange(tag.id)"
            />
            <span class="tag-name">{{ tag.tagName }}</span>
          </label>
        </div>
      </fieldset>
    </div>

    <button
      type="button"
      class="reset-button"
      :disabled="loading"
      @click="handleReset"
    >
      重置筛选
    </button>
  </div>
</template>

<style scoped>
.problem-filters {
  padding: 1.2rem;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent), var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  margin-bottom: 1.2rem;
  box-shadow: var(--shadow-sm);
}

.filters-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.95rem;
}

.filter-section {
  margin: 0;
  min-width: 0;
}

.tag-section {
  grid-column: 1 / -1;
  border: none;
  padding: 0;
}

.filter-label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.48rem;
  color: var(--color-text);
  font-size: 0.82rem;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.tag-legend {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.7rem;
  width: 100%;
}

.tag-toggle-button {
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
}

.tag-toggle-button:hover:not(:disabled) {
  color: var(--color-primary-dark);
}

.tag-toggle-button:focus-visible {
  outline: none;
  border-radius: 6px;
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.tag-toggle-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.filter-select {
  width: 100%;
  min-height: 42px;
  padding: 0 0.78rem;
  border: 1px solid var(--color-border);
  background: var(--color-background);
  color: var(--color-text);
  font-size: 0.88rem;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease;
  border-radius: var(--radius-sm);
}

.filter-select:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: var(--color-background-soft);
}

.filter-select:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.filter-select:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tag-list {
  display: flex;
  gap: 0.56rem;
}

.tag-list-collapsed {
  flex-wrap: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 0.16rem;
  -webkit-overflow-scrolling: touch;
}

.tag-list-collapsed .tag-checkbox {
  flex: 0 0 auto;
}

.tag-list-collapsed::-webkit-scrollbar {
  height: 6px;
}

.tag-list-collapsed::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 999px;
}

.tag-list-expanded {
  flex-wrap: wrap;
  overflow: visible;
  scrollbar-width: none;
}

.tag-list-expanded::-webkit-scrollbar {
  display: none;
}

.tag-checkbox {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 0 0.75rem;
  border: 1px solid var(--color-border);
  background: var(--color-background);
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, background-color 0.15s ease;
  border-radius: 999px;
}

.tag-checkbox:hover {
  border-color: var(--color-primary);
  background: var(--color-background-soft);
  transform: translateY(-1px);
}

.tag-checkbox:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.tag-checkbox input[type='checkbox'] {
  margin-right: 0.45rem;
  width: 15px;
  height: 15px;
  cursor: pointer;
  accent-color: var(--color-primary);
}

.tag-checkbox input[type='checkbox']:checked + .tag-name {
  color: var(--color-primary);
  font-weight: 700;
}

.tag-checkbox input[type='checkbox']:disabled {
  cursor: not-allowed;
}

.tag-name {
  font-size: 0.86rem;
  color: var(--color-text);
  transition: color 0.15s ease;
}

.reset-button {
  margin-top: 0.95rem;
  min-height: 40px;
  padding: 0 1rem;
  border: 1px solid var(--color-border);
  background: var(--color-background-mute);
  color: var(--color-text);
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease;
  border-radius: var(--radius-sm);
}

.reset-button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-dim);
  transform: translateY(-1px);
}

.reset-button:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.reset-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .problem-filters {
    padding: 0.95rem;
  }

  .filters-grid {
    grid-template-columns: 1fr;
  }
}
</style>
