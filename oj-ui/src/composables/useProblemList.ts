import { reactive, ref } from 'vue'
import { problemApi } from '@/api/problem'
import { tagApi } from '@/api/tag'
import type {
  AlgorithmQuestion,
  AlgorithmQuestionType,
  Difficulty,
  ProblemQueryParams,
  Tag,
} from '@/types/problem'

export interface ProblemFilterInput {
  difficulty?: Difficulty
  type?: AlgorithmQuestionType
  tagIds?: number[]
  keyword?: string
}

export function useProblemList() {
  const problems = ref<AlgorithmQuestion[]>([])
  const tags = ref<Tag[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const pagination = reactive({
    current: 1,
    size: 20,
    total: 0,
    pages: 0,
  })

  const filters = reactive<{
    difficulty: Difficulty | undefined
    type: AlgorithmQuestionType
    tagIds: number[]
    keyword: string
  }>({
    difficulty: undefined,
    type: 'AI',
    tagIds: [],
    keyword: '',
  })

  async function loadProblems() {
    loading.value = true
    error.value = null

    try {
      const params: ProblemQueryParams = {
        pageNum: pagination.current,
        pageSize: pagination.size,
        difficulty: filters.difficulty,
        type: filters.type,
        tagIds: filters.tagIds.length > 0 ? filters.tagIds : undefined,
        keyword: filters.keyword || undefined,
      }

      const response = await problemApi.listProblems(params)
      problems.value = response.records
      pagination.total = response.total
      pagination.pages = response.pages
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load problem list'
      problems.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadTags() {
    try {
      tags.value = await tagApi.getAllTags()
    } catch (err) {
      console.error('Failed to load tags:', err)
    }
  }

  function changePage(page: number) {
    pagination.current = page
    loadProblems()
  }

  function applyFilters(newFilters: ProblemFilterInput) {
    if (newFilters.difficulty !== undefined) {
      filters.difficulty = newFilters.difficulty
    }
    if (newFilters.type !== undefined) {
      filters.type = newFilters.type
    }
    if (newFilters.tagIds !== undefined) {
      filters.tagIds = newFilters.tagIds
    }
    if (newFilters.keyword !== undefined) {
      filters.keyword = newFilters.keyword
    }
    pagination.current = 1
    loadProblems()
  }

  function resetFilters() {
    filters.difficulty = undefined
    filters.type = 'AI'
    filters.tagIds = []
    filters.keyword = ''
    pagination.current = 1
    loadProblems()
  }

  return {
    problems,
    tags,
    loading,
    error,
    pagination,
    filters,
    loadProblems,
    loadTags,
    changePage,
    applyFilters,
    resetFilters,
  }
}
