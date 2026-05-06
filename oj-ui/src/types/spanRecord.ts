import type { TraceItem } from './traceItem'
import { getTraceItemStatus } from './traceItem'

export type SpanRecord = TraceItem

export interface SpanRecordPageQuery {
  pageNum: number
  pageSize: number
  traceId?: string
  nodeName?: string
  itemType?: 'LLM' | 'TOOL' | 'NODE'
  status?: 'RUNNING' | 'SUCCESS' | 'FAILED'
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export type SpanStatus = 'running' | 'success' | 'error'

// 兼容旧命名，实际按 trace_item 状态渲染。
export function getSpanStatus(span: SpanRecord): SpanStatus {
  return getTraceItemStatus(span)
}

// 兼容旧视图中的通用数字格式化。
export function formatDuration(ms: number | null): string {
  if (ms === null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}
