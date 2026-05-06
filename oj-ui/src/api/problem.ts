import { http } from '@/utils/http'
import type {
  AlgorithmQuestion,
  CodeExecutionResultVO,
  CodeSubmissionVO,
  CodeSubmissionDetailVO,
  IPage,
  ProblemQueryParams,
} from '@/types/problem'

/**
 * Algorithm question API
 */
export const problemApi = {
  /**
   * Page algorithm questions with optional tag filters.
   */
  listProblems(params: ProblemQueryParams) {
    return http.post<IPage<AlgorithmQuestion>>('/api/algorithm-question/page', {
      pageNum: params.pageNum || params.current || 1,
      pageSize: params.pageSize || params.size || 20,
      difficulty: params.difficulty,
      type: params.type,
      keyword: params.keyword,
      tagIds: params.tagIds && params.tagIds.length > 0 ? params.tagIds : undefined,
    })
  },

  /**
   * Query algorithm question detail by id.
   */
  getProblemById(id: number) {
    return http.get<AlgorithmQuestion>(`/api/algorithm-question/${id}`)
  },

  /**
   * Execute code for a specific question and language.
   */
  executeCode(code: string, algorithmQuestionId: number, language: string) {
    return http.post<CodeExecutionResultVO>('/api/code/execute', { code, algorithmQuestionId, language })
  },

  /**
   * Query submission list.
   */
  getSubmissionList(params: {
    algorithmQuestionId: number
    pageNum?: number
    pageSize?: number
  }) {
    return http.post<IPage<CodeSubmissionVO>>('/api/code/submissions', params)
  },

  /**
   * Query submission detail.
   */
  getSubmissionDetail(id: number) {
    return http.get<CodeSubmissionDetailVO>(`/api/code/submissions/${id}`)
  }
}
