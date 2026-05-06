import { http } from '@/utils/http'
import type { TraceDetail, TracePageQuery, TracePageResult } from '@/types/trace'
import type { TraceItem } from '@/types/traceItem'

export const traceApi = {
  pageTraces(query: TracePageQuery) {
    return http.post<TracePageResult>('/api/trace/page', query)
  },

  getTraceDetail(traceId: string) {
    const encodedTraceId = encodeURIComponent(traceId)
    return http.get<TraceDetail>(`/api/trace/${encodedTraceId}`)
  },

  listTraceItems(traceId: string) {
    const encodedTraceId = encodeURIComponent(traceId)
    return http.get<TraceItem[]>(`/api/trace/${encodedTraceId}/items`)
  },
}

// 保留独立导出，便于旧调用点渐进迁移。
export const pageTraces = traceApi.pageTraces
export const getTraceDetail = traceApi.getTraceDetail
export const listTraceItems = traceApi.listTraceItems
export const getTraceItems = traceApi.listTraceItems
