import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildTraceDetailPath,
  buildTraceListRenderModel,
  formatTraceListRow,
  getTraceStatusTone,
  normalizeTraceListQuery,
  summarizeTraceList,
} from '../SpanListView.vue'

describe('SpanListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('normalizes the list query with only supported trace filters', () => {
    expect(
      normalizeTraceListQuery({
        pageNum: 2,
        pageSize: 20,
        traceId: ' trace-1 ',
        conversationId: ' conversation-1 ',
        requestMessage: '  给我一道动态规划题  ',
        status: 'SUCCESS',
      }),
    ).toEqual({
      pageNum: 2,
      pageSize: 20,
      traceId: 'trace-1',
      conversationId: 'conversation-1',
      requestMessage: '给我一道动态规划题',
      status: 'SUCCESS',
    })
  })

  it('builds trace detail path from traceId', () => {
    expect(buildTraceDetailPath('trace-1')).toBe('/trace/trace-1')
  })

  it('encodes traceId when building trace detail path', () => {
    expect(buildTraceDetailPath('trace/1 a')).toBe('/trace/trace%2F1%20a')
  })

  it('formats list rows around the new trace fields for readability', () => {
    expect(
      formatTraceListRow({
        id: 1,
        traceId: 'trace-1',
        conversationId: 'conversation-1',
        userId: 7,
        requestMessage: '给我一道动态规划题',
        currentAlgorithmQuestionId: 101,
        status: 'FAILED',
        errorMessage: '调用失败',
        createTime: '2026-04-07 10:00:00',
        updateTime: '2026-04-07 10:00:01',
      }),
    ).toEqual({
      id: '1',
      traceId: 'trace-1',
      conversationId: 'conversation-1',
      userId: '7',
      requestMessage: '给我一道动态规划题',
      currentAlgorithmQuestionId: '101',
      statusLabel: '失败',
      statusTone: 'error',
      errorMessage: '调用失败',
      hasError: true,
      createTime: '2026-04-07 10:00:00',
      updateTime: '2026-04-07 10:00:01',
    })
  })

  it('summarizes trace rows by status for the list header', () => {
    expect(
      summarizeTraceList([
        {
          id: 1,
          traceId: 'trace-1',
          conversationId: 'conversation-1',
          userId: 7,
          requestMessage: '给我一道动态规划题',
          currentAlgorithmQuestionId: 101,
          status: 'SUCCESS',
          errorMessage: null,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
        {
          id: 2,
          traceId: 'trace-2',
          conversationId: 'conversation-2',
          userId: 8,
          requestMessage: '评审这段代码',
          currentAlgorithmQuestionId: null,
          status: 'FAILED',
          errorMessage: '超时',
          createTime: '2026-04-07 11:00:00',
          updateTime: '2026-04-07 11:00:01',
        },
      ]),
    ).toEqual({
      total: 2,
      success: 1,
      failed: 1,
      running: 0,
    })
  })

  it('builds the row render model with request message and status label for the timeline', () => {
    expect(
      buildTraceListRenderModel([
        {
          id: 1,
          traceId: 'trace-1',
          conversationId: 'conversation-1',
          userId: 7,
          requestMessage: '给我一道动态规划题',
          currentAlgorithmQuestionId: 101,
          status: 'RUNNING',
          errorMessage: null,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
      ]),
    ).toEqual([
      {
        id: '1',
        traceId: 'trace-1',
        conversationId: 'conversation-1',
        userId: '7',
        requestMessage: '给我一道动态规划题',
        currentAlgorithmQuestionId: '101',
        statusLabel: '执行中',
        statusTone: 'running',
        errorMessage: null,
        hasError: false,
        createTime: '2026-04-07 10:00:00',
        updateTime: '2026-04-07 10:00:01',
      },
    ])
  })

  it('maps running success and error rows to distinct status tones', () => {
    expect(getTraceStatusTone('RUNNING', null)).toBe('running')
    expect(getTraceStatusTone('SUCCESS', null)).toBe('success')
    expect(getTraceStatusTone('FAILED', '调用失败')).toBe('error')
  })
})
