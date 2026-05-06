import { ref, watch, type Ref } from 'vue'
import { problemApi } from '@/api/problem'
import type { CodeSubmissionVO, CodeSubmissionDetailVO } from '@/types/problem'
import { getErrorMessage } from '@/utils/errorUtils'
import { normalizeTestResultsValue } from '@/utils/testCaseResultNormalizer'

function normalizeSubmissionDetail(detail: CodeSubmissionDetailVO): CodeSubmissionDetailVO {
  return {
    ...detail,
    testResults: normalizeTestResultsValue((detail as CodeSubmissionDetailVO & { testResults?: unknown }).testResults),
  }
}

export function useSubmissionList(problemId: Ref<number | undefined>) {
  const submissions = ref<CodeSubmissionVO[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(20)

  const expandedId = ref<number | null>(null)
  const detailLoading = ref(false)
  const detailData = ref<CodeSubmissionDetailVO | null>(null)
  const detailRequestToken = ref(0)

  const loadSubmissions = async (page?: number) => {
    if (!problemId.value) {
      submissions.value = []
      return
    }

    if (page !== undefined) {
      currentPage.value = page
    }

    loading.value = true
    error.value = null

    try {
      const result = await problemApi.getSubmissionList({
        algorithmQuestionId: problemId.value,
        pageNum: currentPage.value,
        pageSize: pageSize.value
      })

      submissions.value = result.records
      total.value = result.total
    } catch (e: unknown) {
      error.value = getErrorMessage(e, '加载提交记录失败，请重试')
      console.error('Failed to load submissions:', e)
    } finally {
      loading.value = false
    }
  }

  const toggleExpand = async (id: number) => {
    if (expandedId.value === id) {
      expandedId.value = null
      detailData.value = null
      detailLoading.value = false
      detailRequestToken.value += 1
      return
    }

    expandedId.value = id
    detailData.value = null
    detailLoading.value = true
    const requestToken = detailRequestToken.value + 1
    detailRequestToken.value = requestToken

    try {
      const nextDetail = await problemApi.getSubmissionDetail(id)
      if (detailRequestToken.value === requestToken && expandedId.value === id) {
        detailData.value = normalizeSubmissionDetail(nextDetail)
      }
    } catch (e: unknown) {
      console.error('Failed to load submission detail:', e)
      if (detailRequestToken.value === requestToken && expandedId.value === id) {
        detailData.value = null
      }
    } finally {
      if (detailRequestToken.value === requestToken && expandedId.value === id) {
        detailLoading.value = false
      }
    }
  }

  watch(problemId, () => {
    currentPage.value = 1
    expandedId.value = null
    detailData.value = null
    detailLoading.value = false
    detailRequestToken.value += 1
    loadSubmissions()
  }, { immediate: true })

  return {
    submissions,
    loading,
    error,
    total,
    currentPage,
    pageSize,
    expandedId,
    detailLoading,
    detailData,
    loadSubmissions,
    toggleExpand
  }
}
