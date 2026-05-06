import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/problem', () => ({
  problemApi: {
    listProblems: vi.fn(),
  },
}))

vi.mock('@/api/tag', () => ({
  tagApi: {
    getAllTags: vi.fn(),
  },
}))

import { problemApi } from '@/api/problem'
import { tagApi } from '@/api/tag'
import { Difficulty } from '@/types/problem'
import { useProblemList } from '../useProblemList'

describe('useProblemList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends difficulty, type, keyword, and tag ids with backend paging params', async () => {
    vi.mocked(problemApi.listProblems).mockResolvedValue({
      records: [],
      total: 0,
      size: 20,
      current: 1,
      pages: 0,
    })

    const { applyFilters } = useProblemList()

    await applyFilters({
      difficulty: Difficulty.MEDIUM,
      type: 'SYSTEM',
      keyword: 'dp',
      tagIds: [1, 2],
    })

    expect(problemApi.listProblems).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 20,
      difficulty: Difficulty.MEDIUM,
      type: 'SYSTEM',
      keyword: 'dp',
      tagIds: [1, 2],
    })
  })

  it('uses AI as the default question type filter', () => {
    const { filters } = useProblemList()
    expect(filters.type).toBe('AI')
  })

  it('loads tags from the slim backend tag contract', async () => {
    vi.mocked(tagApi.getAllTags).mockResolvedValue([
      { id: 1, tagName: '数组' },
    ])

    const { tags, loadTags } = useProblemList()

    await loadTags()

    expect(tags.value).toEqual([{ id: 1, tagName: '数组' }])
  })
  it('maps backend total/pages into problem list pagination state', async () => {
    vi.mocked(problemApi.listProblems).mockResolvedValue({
      records: [],
      total: 41,
      size: 20,
      current: 1,
      pages: 3,
    })

    const { pagination, loadProblems } = useProblemList()

    await loadProblems()

    expect(pagination.total).toBe(41)
    expect(pagination.pages).toBe(3)
  })
})
