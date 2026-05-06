export type TraceStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface TraceListItem {
  id: number
  traceId: string
  conversationId: string | null
  userId: number | null
  requestMessage: string | null
  currentAlgorithmQuestionId: number | null
  status: TraceStatus
  errorMessage: string | null
  createTime: string
  updateTime: string
}

export interface TraceDetail extends TraceListItem {
  currentAlgorithmQuestionSnapshot: string | null
}

export interface TracePageQuery {
  pageNum: number
  pageSize: number
  traceId?: string
  conversationId?: string
  requestMessage?: string
  status?: TraceStatus
}

export interface TracePageResult {
  records: TraceListItem[]
  total: number
  size: number
  current: number
  pages: number
}

// 将后端状态转换为中文展示文案。
export function getTraceStatusLabel(status: TraceStatus): string {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  return '执行中'
}
