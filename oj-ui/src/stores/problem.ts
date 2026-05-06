import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AlgorithmQuestion, Tag } from '@/types/problem'

/**
 * 算法题状态管理
 */
export const useProblemStore = defineStore('problem', () => {
  const currentProblem = ref<AlgorithmQuestion | null>(null)
  const allTags = ref<Tag[]>([])

  function setCurrentProblem(problem: AlgorithmQuestion | null) {
    currentProblem.value = problem
  }

  function setAllTags(tags: Tag[]) {
    allTags.value = tags
  }

  function clearCurrentProblem() {
    currentProblem.value = null
  }

  return {
    currentProblem,
    allTags,
    setCurrentProblem,
    setAllTags,
    clearCurrentProblem
  }
})
