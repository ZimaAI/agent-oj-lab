import { traceApi } from '@/api/trace'
import type { SpanRecord, SpanRecordPageQuery, PageResult } from '@/types/spanRecord'

export const spanRecordApi = {
  listByTraceId(traceId: string) {
    return traceApi.listTraceItems(traceId)
  },

  async pageSpanRecords(query: SpanRecordPageQuery) {
    const current = Math.max(query.pageNum || 1, 1)
    const size = Math.max(query.pageSize || 0, 1)

    if (!query.traceId) {
      return {
        records: [],
        total: 0,
        size,
        current,
        pages: 0,
      } satisfies PageResult<SpanRecord>
    }

    const items = await traceApi.listTraceItems(query.traceId)
    const filtered = items.filter(item => {
      if (query.nodeName && item.nodeName !== query.nodeName) return false
      if (query.itemType && item.itemType !== query.itemType) return false
      if (query.status && item.status !== query.status) return false
      return true
    })
    const total = filtered.length
    const pages = total === 0 ? 0 : Math.ceil(total / size)
    const start = (current - 1) * size
    const records = filtered.slice(start, start + size)

    return {
      records,
      total,
      size,
      current,
      pages,
    } satisfies PageResult<SpanRecord>
  },
}

// 保留旧导出名称，底层已切换到 trace_item 语义。
export const listByTraceId = spanRecordApi.listByTraceId
export const pageSpanRecords = spanRecordApi.pageSpanRecords

