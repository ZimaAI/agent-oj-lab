export type TraceStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export type TraceItemType = 'LLM' | 'TOOL' | 'NODE'

export interface TraceItem {
  id: number
  traceId: string
  nodeName: string
  itemType: TraceItemType
  itemKey: string
  roundNo: number
  toolName: string | null
  inputPayload: string | null
  outputPayload: string | null
  inputSummary: string | null
  outputSummary: string | null
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  status: TraceStatus
  errorMessage: string | null
  startTimestamp: number | null
  endTimestamp: number | null
  createTime: string
  updateTime: string
}

export type TraceItemStatus = 'running' | 'success' | 'error'

// 将后端状态转换为前端展示状态。
export function getTraceItemStatus(item: Pick<TraceItem, 'status'>): TraceItemStatus {
  if (item.status === 'RUNNING') {
    return 'running'
  }
  if (item.status === 'SUCCESS') {
    return 'success'
  }
  return 'error'
}

// 将耗时数字格式化为便于阅读的文本。
export function formatTraceMetric(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '-'
  }
  return `${value}`
}
