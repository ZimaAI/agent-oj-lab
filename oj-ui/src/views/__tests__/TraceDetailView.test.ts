import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'

import {
  buildTraceDetailRenderModel,
  buildTraceDetailSections,
  buildTraceTimelineAxisTicks,
  buildTraceTimelineMetrics,
  buildTraceTimelineWindow,
  formatTraceItemForDisplay,
  setupTraceIdRefetchWatcher,
  summarizeTraceItems,
  toggleTraceItemExpanded,
} from '../TraceDetailView.vue'

describe('TraceDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders axis row and item rows with the same shared timeline grid structure', () => {
    const source = readFileSync(new URL('../TraceDetailView.vue', import.meta.url), 'utf-8')

    expect(source).toContain('grid-template-columns: minmax(220px, 1.4fr) minmax(320px, 1.8fr) minmax(180px, 0.8fr);')
    expect(source).toContain('class="timeline-axis-row timeline-grid-row"')
    expect(source).toContain('class="timeline-axis-cell"')
    expect(source).toContain('class="row-main timeline-grid-row"')
    expect(source).toContain('class="row-track-cell"')
    expect(source).not.toContain('row-time-cell')
  })

  it('imports JsonTreeViewer and uses it for the three json display sections', () => {
    const source = readFileSync(new URL('../TraceDetailView.vue', import.meta.url), 'utf-8')

    expect(source).toContain("import JsonTreeViewer from '@/components/json/JsonTreeViewer.vue'")
    expect(source).toContain('components: { JsonTreeViewer }')
    expect(source).toContain('<JsonTreeViewer :value="sections.questionSnapshot"')
    expect(source).toContain('<JsonTreeViewer :value="item.inputPayload"')
    expect(source).toContain('<JsonTreeViewer :value="item.outputPayload"')
  })

  it('reacts to route traceId changes and refetches detail data', () => {
    const source = readFileSync(new URL('../TraceDetailView.vue', import.meta.url), 'utf-8')

    expect(source).toContain("import { computed, defineComponent, onMounted, ref, watch")
    expect(source).toContain("const traceId = computed(() => route.params.traceId as string)")
    expect(source).toContain('getTraceDetail(traceId.value)')
    expect(source).toContain('listTraceItems(traceId.value)')
    expect(source).toContain('export function setupTraceIdRefetchWatcher')
    expect(source).toContain('return watch(traceIdRef, () => {')
    expect(source).toContain('setupTraceIdRefetchWatcher(traceId, fetchData)')
  })

  it('triggers refetch when traceId ref changes', async () => {
    const traceId = ref('t1')
    const fetchData = vi.fn().mockResolvedValue(undefined)

    const stop = setupTraceIdRefetchWatcher(traceId, fetchData)

    traceId.value = 't2'
    await nextTick()

    expect(fetchData).toHaveBeenCalledTimes(1)

    stop()
  })

  it('builds unique timeline tick keys even when rounded offsets collide', () => {
    const window = {
      windowStart: 0,
      windowEnd: 1,
      span: 1,
      minVisibleMs: 1,
      hasValidRange: true,
    }

    const ticks = buildTraceTimelineAxisTicks(window, 4)
    expect(ticks.map(tick => tick.key)).toEqual([
      'tick-0',
      'tick-1',
      'tick-2',
      'tick-3',
      'tick-4',
    ])
    expect(new Set(ticks.map(tick => tick.key)).size).toBe(ticks.length)
  })

  it('uses non-heading title markup inside the row button', () => {
    const source = readFileSync(new URL('../TraceDetailView.vue', import.meta.url), 'utf-8')

    expect(source).toContain('<span class="item-title">{{ item.title }}</span>')
    expect(source).not.toContain('<h3 class="item-title">')
  })

  it('summarizes trace items with token totals and status counts', () => {
    expect(
      summarizeTraceItems([
        {
          id: 11,
          traceId: 'trace-1',
          nodeName: 'intentRecognitionNode',
          itemType: 'LLM',
          itemKey: 'messages',
          roundNo: 1,
          toolName: null,
          inputPayload: '[{"role":"user"}]',
          outputPayload: '{"intent":"NEW_QUESTION"}',
          inputSummary: '1 条消息',
          outputSummary: '识别成功',
          promptTokens: 12,
          completionTokens: 8,
          totalTokens: 20,
          status: 'SUCCESS',
          errorMessage: null,
          startTimestamp: 1712484000000,
          endTimestamp: 1712484001000,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
        {
          id: 12,
          traceId: 'trace-1',
          nodeName: 'codeEvaluationNode',
          itemType: 'TOOL',
          itemKey: 'executeCode',
          roundNo: 1,
          toolName: 'codeExecutor',
          inputPayload: '{"language":"java"}',
          outputPayload: null,
          inputSummary: '执行 Java 代码',
          outputSummary: null,
          promptTokens: null,
          completionTokens: null,
          totalTokens: null,
          status: 'FAILED',
          errorMessage: '执行超时',
          startTimestamp: 1712484002000,
          endTimestamp: 1712484003000,
          createTime: '2026-04-07 10:00:02',
          updateTime: '2026-04-07 10:00:03',
        },
      ]),
    ).toEqual({
      total: 2,
      success: 1,
      failed: 1,
      running: 0,
      totalTokens: 20,
    })
  })

  it('builds timeline window and aligned metrics using global min and max', () => {
    const items = [
      {
        id: 11,
        traceId: 'trace-1',
        nodeName: 'intentRecognitionNode',
        itemType: 'NODE',
        itemKey: 'intent',
        roundNo: 1,
        toolName: null,
        inputPayload: null,
        outputPayload: null,
        inputSummary: null,
        outputSummary: null,
        promptTokens: null,
        completionTokens: null,
        totalTokens: null,
        status: 'SUCCESS',
        errorMessage: null,
        startTimestamp: 1712484000000,
        endTimestamp: 1712484010000,
        createTime: '2026-04-07 10:00:00',
        updateTime: '2026-04-07 10:00:10',
      },
      {
        id: 12,
        traceId: 'trace-1',
        nodeName: 'codeEvaluationNode',
        itemType: 'TOOL',
        itemKey: 'executeCode',
        roundNo: 1,
        toolName: 'codeExecutor',
        inputPayload: null,
        outputPayload: null,
        inputSummary: null,
        outputSummary: null,
        promptTokens: null,
        completionTokens: null,
        totalTokens: null,
        status: 'SUCCESS',
        errorMessage: null,
        startTimestamp: 1712484005000,
        endTimestamp: 1712484007000,
        createTime: '2026-04-07 10:00:05',
        updateTime: '2026-04-07 10:00:07',
      },
    ]

    const window = buildTraceTimelineWindow(items)
    expect(window.hasValidRange).toBe(true)
    expect(window.span).toBe(10000)
    expect(window.minVisibleMs).toBe(200)

    const metrics = buildTraceTimelineMetrics(items[1], window)
    expect(metrics.leftPct).toBe(50)
    expect(metrics.widthPct).toBe(20)
    expect(metrics.durationText).toBe('2.00s')
  })

  it('falls back to createTime and updateTime when timestamp fields are missing', () => {
    const items = [
      {
        id: 11,
        traceId: 'trace-1',
        nodeName: 'intentRecognitionNode',
        itemType: 'NODE',
        itemKey: 'intent',
        roundNo: 1,
        toolName: null,
        inputPayload: null,
        outputPayload: null,
        inputSummary: null,
        outputSummary: null,
        promptTokens: null,
        completionTokens: null,
        totalTokens: null,
        status: 'SUCCESS',
        errorMessage: null,
        startTimestamp: null,
        endTimestamp: null,
        createTime: '2026-04-07 10:00:00',
        updateTime: '2026-04-07 10:00:10',
      },
    ]

    const window = buildTraceTimelineWindow(items)
    expect(window.hasValidRange).toBe(true)
    expect(window.span).toBe(10000)

    const metrics = buildTraceTimelineMetrics(items[0], window)
    expect(metrics.durationText).toBe('10.0s')
  })

  it('builds shared timeline axis ticks for the top-level axis', () => {
    const window = {
      windowStart: 1712484000000,
      windowEnd: 1712484010000,
      span: 10000,
      minVisibleMs: 200,
      hasValidRange: true,
    }

    expect(buildTraceTimelineAxisTicks(window, 4)).toEqual([
      { key: 'tick-0', leftPct: 0, offsetMs: 0, label: '0ms' },
      { key: 'tick-1', leftPct: 25, offsetMs: 2500, label: '2.50s' },
      { key: 'tick-2', leftPct: 50, offsetMs: 5000, label: '5.00s' },
      { key: 'tick-3', leftPct: 75, offsetMs: 7500, label: '7.50s' },
      { key: 'tick-4', leftPct: 100, offsetMs: 10000, label: '10.0s' },
    ])
  })

  it('keeps rows collapsed by default and toggles expanded state by id', () => {
    const items = [
      {
        id: 12,
        traceId: 'trace-1',
        nodeName: 'codeEvaluationNode',
        itemType: 'TOOL',
        itemKey: 'executeCode',
        roundNo: 2,
        toolName: 'codeExecutor',
        inputPayload: '{"language":"java","input":"1 2"}',
        outputPayload: null,
        inputSummary: '执行 Java 代码',
        outputSummary: null,
        promptTokens: null,
        completionTokens: null,
        totalTokens: null,
        status: 'FAILED',
        errorMessage: '执行超时',
        startTimestamp: 1712484002000,
        endTimestamp: 1712484003000,
        createTime: '2026-04-07 10:00:02',
        updateTime: '2026-04-07 10:00:03',
      },
    ]

    const collapsed = buildTraceDetailRenderModel(items)
    expect(collapsed[0].expanded).toBe(false)

    const expandedIds = toggleTraceItemExpanded(new Set<number>(), 12)
    const expanded = buildTraceDetailRenderModel(items, expandedIds)
    expect(expanded[0].expanded).toBe(true)

    const collapsedAgainIds = toggleTraceItemExpanded(expandedIds, 12)
    const collapsedAgain = buildTraceDetailRenderModel(items, collapsedAgainIds)
    expect(collapsedAgain[0].expanded).toBe(false)
  })

  it('keeps trace item payload raw for json viewer and preserves plain text payloads', () => {
    const jsonItem = {
      id: 11,
      traceId: 'trace-1',
      nodeName: 'intentRecognitionNode',
      itemType: 'NODE',
      itemKey: 'intent',
      roundNo: 1,
      toolName: null,
      inputPayload: '[{"role":"user","content":"给我一道动态规划题"}]',
      outputPayload: '{"intent":"NEW_QUESTION"}',
      inputSummary: '1 条消息',
      outputSummary: '识别成功',
      promptTokens: 12,
      completionTokens: 8,
      totalTokens: 20,
      status: 'SUCCESS',
      errorMessage: null,
      startTimestamp: 1712484000000,
      endTimestamp: 1712484001000,
      createTime: '2026-04-07 10:00:00',
      updateTime: '2026-04-07 10:00:01',
    }

    expect(
      formatTraceItemForDisplay(jsonItem, buildTraceTimelineWindow([jsonItem]), new Set<number>()),
    ).toMatchObject({
      inputPayload: '[{"role":"user","content":"给我一道动态规划题"}]',
      outputPayload: '{"intent":"NEW_QUESTION"}',
      expanded: false,
    })

    const rawItem = {
      ...jsonItem,
      id: 13,
      inputPayload: 'plain text payload',
      outputPayload: '```markdown\n# title\n```',
    }

    expect(
      formatTraceItemForDisplay(rawItem, buildTraceTimelineWindow([rawItem]), new Set<number>()),
    ).toMatchObject({
      inputPayload: 'plain text payload',
      outputPayload: '```markdown\n# title\n```',
      expanded: false,
    })
  })

  it('builds trace detail sections from detail and item data with raw snapshot payload', () => {
    expect(
      buildTraceDetailSections(
        {
          id: 1,
          traceId: 'trace-1',
          conversationId: 'conversation-1',
          userId: 7,
          requestMessage: '给我一道动态规划题',
          currentAlgorithmQuestionId: 101,
          currentAlgorithmQuestionSnapshot: '{"title":"动态规划入门"}',
          status: 'SUCCESS',
          errorMessage: null,
          createTime: '2026-04-07 10:00:00',
          updateTime: '2026-04-07 10:00:01',
        },
        [
          {
            id: 11,
            traceId: 'trace-1',
            nodeName: 'intentRecognitionNode',
            itemType: 'NODE',
            itemKey: 'intent',
            roundNo: 1,
            toolName: null,
            inputPayload: null,
            outputPayload: '{"intent":"NEW_QUESTION"}',
            inputSummary: null,
            outputSummary: '识别成功',
            promptTokens: 12,
            completionTokens: 8,
            totalTokens: 20,
            status: 'SUCCESS',
            errorMessage: null,
            startTimestamp: 1712484000000,
            endTimestamp: 1712484001000,
            createTime: '2026-04-07 10:00:00',
            updateTime: '2026-04-07 10:00:01',
          },
        ],
      ),
    ).toEqual({
      overview: [
        ['Trace ID', 'trace-1'],
        ['Conversation ID', 'conversation-1'],
        ['用户 ID', '7'],
        ['题目 ID', '101'],
        ['状态', '成功'],
      ],
      requestMessage: '给我一道动态规划题',
      questionSnapshot: '{"title":"动态规划入门"}',
      summary: {
        total: 1,
        success: 1,
        failed: 0,
        running: 0,
        totalTokens: 20,
      },
    })
  })
})
