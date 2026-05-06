import { http } from '@/utils/http'

export type AdminDifficulty = 'SIMPLE' | 'MEDIUM' | 'HARD'
export type AdminQuestionType = 'AI' | 'SYSTEM'

export interface AdminQuestionTag {
  id: number
  tagName: string
}

export interface AdminQuestionListItem {
  id: number
  title: string
  description: string
  difficulty: AdminDifficulty | null
  type: AdminQuestionType | null
  language: string | null
  functionName: string | null
  createTime: string
  updateTime: string
  tags: AdminQuestionTag[]
}

export interface AdminQuestionPageRequest {
  pageNum?: number
  pageSize?: number
  current?: number
  size?: number
  keyword?: string
  difficulty?: AdminDifficulty
  type?: AdminQuestionType
}

export interface AdminQuestionPage {
  current: number
  size: number
  total: number
  pages: number
  records: AdminQuestionListItem[]
}

export interface AdminQuestionQueryParams {
  pageNum?: number
  pageSize?: number
  current?: number
  size?: number
  keyword?: string
  difficulty?: AdminDifficulty
  type?: AdminQuestionType
}

export interface AdminQuestionDetail {
  id: number
  title: string
  description: string
  difficulty: string | null
  type: AdminQuestionType | null
  standardCasePool?: AdminStandardCase[] | null
  language?: string | null
  functionName?: string | null
  codeSkeleton?: string | null
  referenceAnswer?: string | null
  testCases?: string | null
  vectorSyncStatus: string | null
  vectorSyncErrorMessage: string | null
  isDelete: number
  updateTime: string
  tags?: string[] | null
}

export interface AdminQuestionProjection {
  questionId: number
  title: string | null
  difficulty: string | null
  language: string | null
  tagsJson: string | null
  embeddingText: string | null
  syncStatus: string | null
  syncErrorMessage: string | null
  isDelete: number
  updateTime: string
}

export interface AdminQuestionDualDetail {
  mysqlQuestion: AdminQuestionDetail
  vectorProjection?: AdminQuestionProjection | null
  postgresProjection?: AdminQuestionProjection | null
}

export interface AdminQuestionUpdateRequest {
  title?: string
  description?: string
  difficulty?: AdminDifficulty
  standardCasePool?: AdminStandardCase[]
  tags?: string[]
  codeTemplates?: AdminQuestionCodeTemplateUpdateRequest[]
}

export interface AdminQuestionCodeTemplateCreateRequest {
  language: 'JAVA' | 'PYTHON' | 'JAVASCRIPT'
  entryMethodName?: string
  starterCode: string
  referenceAnswer: string
}

export interface AdminQuestionCodeTemplateUpdateRequest {
  language: 'JAVA' | 'PYTHON' | 'JAVASCRIPT'
  entryMethodName?: string
  starterCode?: string
  referenceAnswer?: string
}

export interface AdminStandardCase {
  stdin: string
  expectedStdout: string
  publicCase: boolean
  description?: string
}

export interface AdminQuestionCreateRequest {
  title: string
  description: string
  difficulty: AdminDifficulty
  standardCasePool: AdminStandardCase[]
  tagIds?: number[]
  tags?: string[]
  codeTemplates: AdminQuestionCodeTemplateCreateRequest[]
}

export interface AdminQuestionDocumentVO {
  docId: number
  docTitle: string | null
  status: string | null
  docUrl: string | null
  convertedDocUrl: string | null
  createdAt: string | null
  updatedAt: string | null
  progressPercent: number
}

export interface AdminQuestionDocumentSegmentVO {
  segmentId: number
  documentId: number
  chunkOrder: number | null
  chunkId: string | null
  parentChunkId: string | null
  hitkQuestion: string | null
  status: string | null
  skipEmbedding: number | null
  text: string | null
  metadata: string | null
  parentSegment: boolean
  childSegmentCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminQuestionDocumentSegmentPageRequest {
  current?: number
  size?: number
}

export interface AdminQuestionDocumentSegmentPageResponse {
  current: number
  size: number
  total: number
  records: AdminQuestionDocumentSegmentVO[]
}

export interface AdminKnowledgeSegmentPageRequest {
  current?: number
  size?: number
  keyword?: string
}

export interface AdminKnowledgeSegmentVO {
  segmentId: number
  questionId: number | null
  questionTitle: string | null
  documentId: number | null
  documentTitle: string | null
  chunkOrder: number | null
  chunkId: string | null
  status: string | null
  skipEmbedding: number | null
  hitkQuestion: string | null
  text: string | null
  metadata: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminKnowledgeSegmentPageResponse {
  current: number
  size: number
  total: number
  records: AdminKnowledgeSegmentVO[]
}

export interface AdminQuestionDocumentSegmentHitkQuestionGenerateRequest {
  segmentIds: number[]
}

export interface AdminQuestionDocumentSegmentHitkQuestionUpdateItem {
  segmentId: number
  hitkQuestion: string
}

export interface AdminQuestionDocumentSegmentHitkQuestionUpdateRequest {
  updates: AdminQuestionDocumentSegmentHitkQuestionUpdateItem[]
}

export interface AdminQuestionDocumentSegmentHitkQuestionResult {
  segmentId: number
  success: boolean
  message: string
  hitkQuestion: string | null
}

export interface AdminQuestionDocumentSegmentHitkQuestionBatchResponse {
  successCount: number
  failureCount: number
  results: AdminQuestionDocumentSegmentHitkQuestionResult[]
}

export interface AdminQuestionDocumentSegmentHitkTestRequest {
  segmentIds: number[]
}

export interface AdminQuestionDocumentSegmentHitkTestDetail {
  segmentId: number
  hitkQuestion: string | null
  retrievedSegmentIds: number[]
  hit: boolean
}

export interface AdminQuestionDocumentSegmentRagasVO {
  id?: number | null
  ragasId?: number | null
  segmentId: number
  question?: string | null
  standardAnswer?: string | null
  generatedAnswer?: string | null
  contextPrecision?: number | null
  contextRecall?: number | null
  faithfulness?: number | null
  answerRelevancy?: number | null
  answerSimilarity?: number | null
  answerCorrectness?: number | null
  status?: string | null
  metadata?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface AdminQuestionDocumentSegmentRagasGenerateRequest {
  segmentIds: number[]
}

export interface AdminQuestionDocumentSegmentRagasUpdateItem {
  id?: number
  ragasId?: number
  segmentId: number
  question: string
  standardAnswer: string
}

export interface AdminQuestionDocumentSegmentRagasUpdateRequest {
  updates: AdminQuestionDocumentSegmentRagasUpdateItem[]
}

export interface AdminQuestionDocumentSegmentRagasDeleteRequest {
  ids?: number[]
  ragasIds?: number[]
  segmentIds?: number[]
}

export interface AdminQuestionDocumentSegmentRagasListRequest {
  segmentIds?: number[]
  ragasIds?: number[]
  ids?: number[]
}

export interface AdminQuestionDocumentSegmentRagasAnswerGenerateRequest {
  segmentIds?: number[]
  ragasIds?: number[]
  ids?: number[]
}

export interface AdminQuestionDocumentSegmentRagasEvaluateRequest {
  segmentIds?: number[]
  ragasIds?: number[]
  ids?: number[]
}

export interface AdminRagasTaskCreateRequest {
  segmentIds: number[]
}

export interface AdminRagasTaskDetailItem {
  ragasId: number | null
  segmentId: number
  questionId: number | null
  documentId: number | null
  status: string | null
  errorMessage: string | null
  answerRelevancy: number | null
  faithfulness: number | null
  contextPrecision: number | null
  contextRecall: number | null
  overallScore: number | null
  ragasQuestion: string | null
  standardAnswer: string | null
  generatedAnswer: string | null
}

export interface AdminRagasTaskVO {
  taskId: number
  questionId: number
  documentId: number
  totalCount: number
  successCount: number
  failureCount: number
  averageAnswerRelevancy: number | null
  averageFaithfulness: number | null
  averageContextPrecision: number | null
  averageContextRecall: number | null
  status: string | null
  errorMessage: string | null
  createTime: string | null
  details: AdminRagasTaskDetailItem[]
}

export interface AdminRagasTaskDetailVO {
  taskId: number
  questionId: number
  documentId: number
  totalCount: number
  successCount: number
  failureCount: number
  averageAnswerRelevancy: number | null
  averageFaithfulness: number | null
  averageContextPrecision: number | null
  averageContextRecall: number | null
  status: string | null
  errorMessage: string | null
  createTime: string | null
  details: AdminRagasTaskDetailItem[]
}

export interface AdminRagasTaskPageRequest {
  current?: number
  size?: number
  status?: string
  startTime?: string
  endTime?: string
}

export interface AdminRagasTaskPageResponse {
  current: number
  size: number
  total: number
  records: AdminRagasTaskVO[]
}

export interface AdminHitkTaskVO {
  taskId: number
  questionId: number
  documentId: number
  totalCount: number
  hitCount: number
  missCount: number
  hitRate: number
  status: string | null
  errorMessage: string | null
  remark: string | null
  createTime: string | null
  details: AdminQuestionDocumentSegmentHitkTestDetail[]
}

export interface AdminHitkTaskDetailRetrievedSegment {
  segmentId: number
  documentId: number | null
  chunkOrder: number | null
  text: string | null
  rawSimilarity: number | null
  similarityScore: number | null
  rrfScore: number | null
  finalScore: number | null
}

export interface AdminHitkTaskDetailSegmentResult {
  segmentId: number
  segmentText: string | null
  hitkQuestion: string | null
  rewrittenQuestions: string[]
  retrievedSegments: AdminHitkTaskDetailRetrievedSegment[]
  hit: boolean
}

export interface AdminHitkTaskDetailVO {
  taskId: number
  questionId: number
  documentId: number
  totalCount: number
  hitCount: number
  missCount: number
  hitRate: number
  status: string | null
  errorMessage: string | null
  remark: string | null
  createTime: string | null
  segmentResults: AdminHitkTaskDetailSegmentResult[]
}

export interface AdminHitkTaskPageRequest {
  current?: number
  size?: number
  status?: string
  startTime?: string
  endTime?: string
}

export interface AdminHitkTaskPageResponse {
  current: number
  size: number
  total: number
  records: AdminHitkTaskVO[]
}

export interface AdminHitkTaskBatchStatisticsRequest {
  taskIds: number[]
}

export interface AdminHitkTaskStatisticsResponse {
  taskCount: number
  averageHitRate: number
  averageTotalCount: number
  averageHitCount: number
  averageMissCount: number
  sumTotalCount: number
  sumHitCount: number
  sumMissCount: number
  overallHitRate: number
}

export interface AdminHitkTaskBatchDeleteRequest {
  taskIds: number[]
}

export interface AdminHitkTaskBatchDeleteItemResult {
  taskId: number
  success: boolean
  message: string
}

export interface AdminHitkTaskBatchDeleteResponse {
  successCount: number
  failureCount: number
  results: AdminHitkTaskBatchDeleteItemResult[]
}

export interface AdminHitkTaskRemarkUpdateRequest {
  remark: string | null
}

export interface AdminBatchDeleteItemResult {
  questionId: number
  success: boolean
  message: string
}

export interface AdminBatchDeleteResponse {
  successCount: number
  failureCount: number
  results: AdminBatchDeleteItemResult[]
}

export interface AdminCompensationTask {
  taskId: number
  questionId: number
  operationType: string
  status: string
  errorMessage: string | null
  retryCount: number
}

const toPositiveInt = (value: number | undefined, fallback: number) => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
    return fallback
  }
  return Math.floor(value)
}

export const adminQuestionApi = {
  pageQuestions(params: AdminQuestionQueryParams) {
    const pageNum = toPositiveInt(params.pageNum ?? params.current, 1)
    const pageSize = toPositiveInt(params.pageSize ?? params.size, 20)
    return http.post<AdminQuestionPage>('/api/algorithm-question/page', {
      pageNum,
      pageSize,
      current: pageNum,
      size: pageSize,
      keyword: params.keyword,
      difficulty: params.difficulty,
      type: params.type,
    })
  },

  getDualDetail(questionId: number) {
    return http.get<AdminQuestionDualDetail>(`/api/admin/questions/${questionId}/dual-detail`)
  },

  listTags() {
    return http.get<AdminQuestionTag[]>('/api/tag/list')
  },

  createQuestion(data: AdminQuestionCreateRequest) {
    return http.post<AdminQuestionDualDetail>('/api/admin/questions', data)
  },

  uploadQuestionDocument(questionId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<AdminQuestionDocumentVO>(`/api/admin/questions/${questionId}/documents/upload`, formData)
  },

  listQuestionDocuments(questionId: number) {
    return http.get<AdminQuestionDocumentVO[]>(`/api/admin/questions/${questionId}/documents`)
  },

  deleteQuestionDocument(questionId: number, docId: number) {
    return http.request<boolean>(`/api/admin/questions/${questionId}/documents/${docId}`, {
      method: 'DELETE',
    })
  },

  pageQuestionDocumentSegments(
    questionId: number,
    docId: number,
    params: AdminQuestionDocumentSegmentPageRequest,
  ) {
    const current = toPositiveInt(params.current, 1)
    const size = toPositiveInt(params.size, 10)
    return http.get<AdminQuestionDocumentSegmentPageResponse>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments`,
      { current, size },
    )
  },

  pageKnowledgeSegments(params: AdminKnowledgeSegmentPageRequest) {
    const current = toPositiveInt(params.current, 1)
    const size = toPositiveInt(params.size, 20)
    return http.get<AdminKnowledgeSegmentPageResponse>('/api/admin/questions/knowledge-segments', {
      current,
      size,
      keyword: params.keyword,
    })
  },

  listQuestionDocumentChildSegments(questionId: number, docId: number, parentSegmentId: number) {
    return http.get<AdminQuestionDocumentSegmentVO[]>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/${parentSegmentId}/children`,
    )
  },

  generateQuestionDocumentSegmentRagas(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentRagasGenerateRequest,
  ) {
    return http.post<AdminQuestionDocumentSegmentRagasVO[]>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/ragas/generate`,
      data,
    )
  },

  listQuestionDocumentSegmentRagas(
    questionId: number,
    docId: number,
    params: AdminQuestionDocumentSegmentRagasListRequest = {},
  ) {
    return http.get<AdminQuestionDocumentSegmentRagasVO[]>(`/api/admin/questions/${questionId}/documents/${docId}/segments/ragas`, {
      segmentIds: params.segmentIds?.join(','),
      ragasIds: params.ragasIds?.join(','),
      ids: params.ids?.join(','),
    })
  },

  updateQuestionDocumentSegmentRagas(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentRagasUpdateRequest,
  ) {
    return http.put<AdminQuestionDocumentSegmentRagasVO[]>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/ragas`,
      data,
    )
  },

  deleteQuestionDocumentSegmentRagas(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentRagasDeleteRequest,
  ) {
    return http.request<void>(`/api/admin/questions/${questionId}/documents/${docId}/segments/ragas`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    })
  },

  generateQuestionDocumentSegmentRagasAnswers(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentRagasAnswerGenerateRequest,
  ) {
    return http.post<AdminQuestionDocumentSegmentRagasVO[]>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/ragas/answers/generate`,
      data,
    )
  },

  evaluateQuestionDocumentSegmentRagas(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentRagasEvaluateRequest,
  ) {
    return http.post<AdminQuestionDocumentSegmentRagasVO[]>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/ragas/evaluate`,
      data,
    )
  },

  createQuestionDocumentSegmentRagasTask(
    questionId: number,
    docId: number,
    data: AdminRagasTaskCreateRequest,
  ) {
    return http.post<AdminRagasTaskVO>(`/api/admin/questions/${questionId}/documents/${docId}/segments/ragas/tasks`, data)
  },

  listQuestionDocumentSegmentRagasTasks(questionId: number, docId: number) {
    return http.get<AdminRagasTaskVO[]>(`/api/admin/questions/${questionId}/documents/${docId}/segments/ragas/tasks`)
  },

  pageRagasTasks(params: AdminRagasTaskPageRequest) {
    const current = toPositiveInt(params.current, 1)
    const size = toPositiveInt(params.size, 20)
    return http.get<AdminRagasTaskPageResponse>('/api/admin/questions/ragas-tasks', {
      current,
      size,
      status: params.status,
      startTime: params.startTime,
      endTime: params.endTime,
    })
  },

  getRagasTaskDetail(taskId: number) {
    return http.get<AdminRagasTaskDetailVO>(`/api/admin/questions/ragas-tasks/${taskId}`)
  },

  generateQuestionDocumentSegmentHitkQuestion(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentHitkQuestionGenerateRequest,
  ) {
    return http.post<AdminQuestionDocumentSegmentHitkQuestionBatchResponse>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/hitk-question/generate`,
      data,
    )
  },

  updateQuestionDocumentSegmentHitkQuestion(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentHitkQuestionUpdateRequest,
  ) {
    return http.put<AdminQuestionDocumentSegmentHitkQuestionBatchResponse>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/hitk-question`,
      data,
    )
  },

  createQuestionDocumentSegmentHitkTestTask(
    questionId: number,
    docId: number,
    data: AdminQuestionDocumentSegmentHitkTestRequest,
  ) {
    return http.post<AdminHitkTaskVO>(`/api/admin/questions/${questionId}/documents/${docId}/segments/hitk-tests`, data)
  },

  listQuestionDocumentSegmentHitkTestTasks(questionId: number, docId: number) {
    return http.get<AdminHitkTaskVO[]>(`/api/admin/questions/${questionId}/documents/${docId}/segments/hitk-tests`)
  },

  getQuestionDocumentSegmentHitkTestTaskDetail(questionId: number, docId: number, taskId: number) {
    return http.get<AdminHitkTaskDetailVO>(
      `/api/admin/questions/${questionId}/documents/${docId}/segments/hitk-tests/${taskId}`,
    )
  },

  pageHitkTasks(params: AdminHitkTaskPageRequest) {
    const current = toPositiveInt(params.current, 1)
    const size = toPositiveInt(params.size, 20)
    return http.get<AdminHitkTaskPageResponse>('/api/admin/questions/hitk-tasks', {
      current,
      size,
      status: params.status,
      startTime: params.startTime,
      endTime: params.endTime,
    })
  },

  calculateHitkTaskStatistics(data: AdminHitkTaskBatchStatisticsRequest) {
    return http.post<AdminHitkTaskStatisticsResponse>('/api/admin/questions/hitk-tasks/statistics', data)
  },

  batchDeleteHitkTasks(data: AdminHitkTaskBatchDeleteRequest) {
    return http.post<AdminHitkTaskBatchDeleteResponse>('/api/admin/questions/hitk-tasks/batch-delete', data)
  },

  updateHitkTaskRemark(taskId: number, data: AdminHitkTaskRemarkUpdateRequest) {
    return http.put<AdminHitkTaskVO>(`/api/admin/questions/hitk-tasks/${taskId}/remark`, data)
  },

  updateQuestion(questionId: number, data: AdminQuestionUpdateRequest) {
    return http.put<AdminQuestionDualDetail>(`/api/admin/questions/${questionId}`, data)
  },

  batchDeleteQuestions(questionIds: number[]) {
    return http.post<AdminBatchDeleteResponse>('/api/admin/questions/batch-delete', { questionIds })
  },

  listPendingCompensationTasks() {
    return http.get<AdminCompensationTask[]>('/api/admin/questions/compensation/pending')
  },

  retryCompensationTask(taskId: number) {
    return http.post<AdminCompensationTask>(`/api/admin/questions/compensation/${taskId}/retry`)
  },
}
