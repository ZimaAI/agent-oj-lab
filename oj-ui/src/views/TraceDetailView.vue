<script lang="ts">
import { computed, defineComponent, onMounted, ref, watch, type WatchSource, type WatchStopHandle } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getTraceDetail, listTraceItems } from '@/api/trace'
import JsonTreeViewer from '@/components/json/JsonTreeViewer.vue'
import { getTraceStatusLabel, type TraceDetail } from '@/types/trace'
import { getTraceItemStatus, type TraceItem } from '@/types/traceItem'

type JsonDisplayValue = string | Record<string, unknown> | unknown[] | null

interface TraceItemDisplay {
  id: number
  title: string
  subtitle: string
  statusLabel: string
  statusClass: 'running' | 'success' | 'error'
  toolName: string | null
  inputPayload: JsonDisplayValue
  outputPayload: JsonDisplayValue
  inputSummary: string | null
  outputSummary: string | null
  tokenText: string
  errorMessage: string | null
  createTime: string
  updateTime: string
  startTimestamp: number | null
  endTimestamp: number | null
  leftPct: number
  widthPct: number
  durationText: string
  startMs: number | null
  endMs: number | null
  expanded: boolean
}

export interface TraceTimelineWindow {
  windowStart: number
  windowEnd: number
  span: number
  minVisibleMs: number
  hasValidRange: boolean
}

export interface TraceTimelineMetrics {
  startMs: number | null
  endMs: number | null
  leftPct: number
  widthPct: number
  durationText: string
}

export interface TraceTimelineAxisTick {
  key: string
  leftPct: number
  offsetMs: number
  label: string
}

const MIN_VISIBLE_PCT = 2
const FALLBACK_BAR_WIDTH_PCT = 4

interface TraceItemSummary {
  total: number
  success: number
  failed: number
  running: number
  totalTokens: number
}

interface TraceDetailSections {
  overview: Array<[string, string]>
  requestMessage: string
  questionSnapshot: JsonDisplayValue
  summary: TraceItemSummary
}

// 监听 traceId 变化并触发详情重拉，返回 stop 句柄便于测试和清理。
export function setupTraceIdRefetchWatcher(
  traceIdRef: WatchSource<string>,
  fetchData: () => Promise<unknown>,
): WatchStopHandle {
  return watch(traceIdRef, () => {
    void fetchData()
  })
}

// 将 token 统计格式化为单行摘要。
function formatTokenText(item: TraceItem): string {
  const prompt = item.promptTokens ?? 0
  const completion = item.completionTokens ?? 0
  const total = item.totalTokens ?? 0
  return `Prompt ${prompt} / Completion ${completion} / Total ${total}`
}

// 解析 trace_item 时间戳，单位为毫秒。
function parseTraceTimestamp(value: number | null | undefined): number | null {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return null
  }
  return value
}

// 解析时间文本，兼容 "YYYY-MM-DD HH:mm:ss" 这类常见格式。
function parseTraceTimeMs(value: string | null | undefined): number | null {
  if (!value) {
    return null
  }
  const normalized = value.trim().replace(' ', 'T')
  const parsed = Date.parse(normalized)
  if (Number.isNaN(parsed)) {
    return null
  }
  return parsed
}

// 解析条目开始时间，优先使用毫秒时间戳，缺失时回退到 createTime。
function resolveTraceStartMs(item: TraceItem): number | null {
  return parseTraceTimestamp(item.startTimestamp) ?? parseTraceTimeMs(item.createTime)
}

// 解析条目结束时间，优先使用毫秒时间戳，缺失时回退到 updateTime。
function resolveTraceEndMs(item: TraceItem): number | null {
  return parseTraceTimestamp(item.endTimestamp) ?? parseTraceTimeMs(item.updateTime)
}

// 格式化时长文本，优先展示毫秒级信息。
function formatDurationText(durationMs: number): string {
  if (durationMs < 1000) {
    return `${Math.max(0, durationMs)}ms`
  }
  const seconds = durationMs / 1000
  if (seconds < 60) {
    return `${seconds.toFixed(seconds >= 10 ? 1 : 2)}s`
  }
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = (seconds % 60).toFixed(1)
  return `${minutes}m ${remainSeconds}s`
}

// 构建全局时间窗口，用于每个节点条目的区间定位。
export function buildTraceTimelineWindow(items: TraceItem[]): TraceTimelineWindow {
  const validStartPoints = items
    .map((item) => resolveTraceStartMs(item))
    .filter((ms): ms is number => ms !== null)
  const validEndPoints = items
    .map((item) => resolveTraceEndMs(item))
    .filter((ms): ms is number => ms !== null)

  if (validStartPoints.length === 0 || validEndPoints.length === 0) {
    return {
      windowStart: 0,
      windowEnd: 1,
      span: 1,
      minVisibleMs: 1,
      hasValidRange: false,
    }
  }

  const windowStart = Math.min(...validStartPoints)
  const windowEnd = Math.max(...validEndPoints)
  const span = Math.max(windowEnd - windowStart, 1)

  return {
    windowStart,
    windowEnd,
    span,
    minVisibleMs: Math.max(Math.floor(span * (MIN_VISIBLE_PCT / 100)), 1),
    hasValidRange: true,
  }
}

// 基于全局窗口计算单条 trace_item 的时间轴指标。
export function buildTraceTimelineMetrics(item: TraceItem, window: TraceTimelineWindow): TraceTimelineMetrics {
  const startMs = resolveTraceStartMs(item)
  const endMs = resolveTraceEndMs(item)

  if (startMs === null || endMs === null || !window.hasValidRange) {
    return {
      startMs,
      endMs,
      leftPct: 0,
      widthPct: FALLBACK_BAR_WIDTH_PCT,
      durationText: '时间未知',
    }
  }

  const normalizedEnd = Math.max(endMs, startMs)
  const duration = normalizedEnd - startMs
  const durationForBar = Math.max(duration, window.minVisibleMs)
  const rawLeftPct = ((startMs - window.windowStart) / window.span) * 100
  const widthPct = Math.min(Math.max((durationForBar / window.span) * 100, MIN_VISIBLE_PCT), 100)
  const leftPct = Math.max(Math.min(rawLeftPct, 100 - widthPct), 0)

  return {
    startMs,
    endMs: normalizedEnd,
    leftPct,
    widthPct,
    durationText: formatDurationText(duration),
  }
}

// 构建共享时间轴刻度，供列表顶部统一时间轴展示。
export function buildTraceTimelineAxisTicks(window: TraceTimelineWindow, segments = 4): TraceTimelineAxisTick[] {
  if (!window.hasValidRange || window.span <= 0 || segments <= 0) {
    return []
  }

  const ticks: TraceTimelineAxisTick[] = []
  for (let index = 0; index <= segments; index += 1) {
    const ratio = index / segments
    const offsetMs = Math.round(window.span * ratio)
    ticks.push({
      key: `tick-${index}`,
      leftPct: ratio * 100,
      offsetMs,
      label: formatDurationText(offsetMs),
    })
  }
  return ticks
}

// 切换条目展开状态，返回新的 id 集合避免共享状态。
export function toggleTraceItemExpanded(expandedIds: Set<number>, id: number): Set<number> {
  const next = new Set(expandedIds)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  return next
}

// 汇总 trace_item 状态与 token 总数。
export function summarizeTraceItems(items: TraceItem[]): TraceItemSummary {
  return items.reduce<TraceItemSummary>((summary, item) => {
    summary.total += 1
    summary.totalTokens += item.totalTokens ?? 0
    if (item.status === 'SUCCESS') summary.success += 1
    else if (item.status === 'FAILED') summary.failed += 1
    else summary.running += 1
    return summary
  }, {
    total: 0,
    success: 0,
    failed: 0,
    running: 0,
    totalTokens: 0,
  })
}

// 将 trace_item 转换为详情页展示块。
export function formatTraceItemForDisplay(
  item: TraceItem,
  window: TraceTimelineWindow,
  expandedIds: Set<number> = new Set(),
): TraceItemDisplay {
  const timeline = buildTraceTimelineMetrics(item, window)
  return {
    id: item.id,
    title: `${item.nodeName} · ${item.itemType}`,
    subtitle: `轮次 ${item.roundNo} · Key: ${item.itemKey}`,
    statusLabel: getTraceStatusLabel(item.status),
    statusClass: getTraceItemStatus(item),
    toolName: item.toolName,
    inputPayload: item.inputPayload,
    outputPayload: item.outputPayload,
    inputSummary: item.inputSummary,
    outputSummary: item.outputSummary,
    tokenText: formatTokenText(item),
    errorMessage: item.errorMessage,
    createTime: item.createTime,
    updateTime: item.updateTime,
    startTimestamp: item.startTimestamp,
    endTimestamp: item.endTimestamp,
    leftPct: timeline.leftPct,
    widthPct: timeline.widthPct,
    durationText: timeline.durationText,
    startMs: timeline.startMs,
    endMs: timeline.endMs,
    expanded: expandedIds.has(item.id),
  }
}

// 构建详情页条目渲染模型，便于测试页面展示意图。
export function buildTraceDetailRenderModel(items: TraceItem[], expandedIds: Set<number> = new Set()): TraceItemDisplay[] {
  const window = buildTraceTimelineWindow(items)
  return items.map((item) => formatTraceItemForDisplay(item, window, expandedIds))
}

// 组装详情页概览区块。
export function buildTraceDetailSections(detail: TraceDetail, items: TraceItem[]): TraceDetailSections {
  return {
    overview: [
      ['Trace ID', detail.traceId],
      ['Conversation ID', detail.conversationId || '-'],
      ['用户 ID', detail.userId === null ? '-' : `${detail.userId}`],
      ['题目 ID', detail.currentAlgorithmQuestionId === null ? '-' : `${detail.currentAlgorithmQuestionId}`],
      ['状态', getTraceStatusLabel(detail.status)],
    ],
    requestMessage: detail.requestMessage || '-',
    questionSnapshot: detail.currentAlgorithmQuestionSnapshot,
    summary: summarizeTraceItems(items),
  }
}

export default defineComponent({
  name: 'TraceDetailView',
  components: { JsonTreeViewer },
  setup() {
    const route = useRoute()
    const router = useRouter()
    // 基于当前路由参数实时计算 traceId。
    const traceId = computed(() => route.params.traceId as string)

    const loading = ref(false)
    const error = ref('')
    const detail = ref<TraceDetail | null>(null)
    const items = ref<TraceItem[]>([])
    const expandedIds = ref<Set<number>>(new Set())
    const failedOnly = ref(false)

    // 拉取 trace 详情与 trace_item 列表。
    async function fetchData() {
      loading.value = true
      error.value = ''
      try {
        const [traceDetail, traceItems] = await Promise.all([
          getTraceDetail(traceId.value),
          listTraceItems(traceId.value),
        ])
        detail.value = traceDetail
        items.value = traceItems
        expandedIds.value = new Set()
      } catch (e: any) {
        error.value = e.message || '加载失败'
        detail.value = null
        items.value = []
        expandedIds.value = new Set()
      } finally {
        loading.value = false
      }
    }

    // 输出概览、请求与题目快照信息。
    const sections = computed(() => {
      if (!detail.value) {
        return null
      }
      return buildTraceDetailSections(detail.value, items.value)
    })

    // 将 trace_item 映射为可直接渲染的卡片数据。
    const displayItems = computed(() => {
      const renderModel = buildTraceDetailRenderModel(items.value, expandedIds.value)
      if (!failedOnly.value) {
        return renderModel
      }
      return renderModel.filter((item) => item.statusClass === 'error')
    })

    // 构建顶部共享时间轴窗口。
    const timelineWindow = computed(() => buildTraceTimelineWindow(items.value))

    // 构建顶部共享时间轴刻度。
    const timelineTicks = computed(() => buildTraceTimelineAxisTicks(timelineWindow.value, 4))

    // 切换单个条目展开态。
    function toggleExpanded(id: number) {
      expandedIds.value = toggleTraceItemExpanded(expandedIds.value, id)
    }

    const allExpanded = computed(() => {
      return items.value.length > 0 && expandedIds.value.size >= items.value.length
    })

    // 批量展开/折叠，减少排障时的重复点击操作。
    function toggleExpandAll() {
      if (allExpanded.value) {
        expandedIds.value = new Set()
        return
      }
      expandedIds.value = new Set(items.value.map((item) => item.id))
    }

    // 统一格式化毫秒时间戳，提升排障阅读效率。
    function formatTimestamp(value: number | null): string {
      if (value === null || value === undefined) {
        return '-'
      }
      return new Date(value).toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    }

    // 页面首次加载时获取详情数据。
    onMounted(() => {
      void fetchData()
    })

    // 同组件实例切换 traceId 时重新拉取最新详情。
    setupTraceIdRefetchWatcher(traceId, fetchData)

    return {
      displayItems,
      error,
      getTraceItemStatus,
      goBack: () => router.back(),
      loading,
      sections,
      formatTimestamp,
      failedOnly,
      allExpanded,
      toggleExpandAll,
      timelineTicks,
      timelineWindow,
      toggleExpanded,
      traceId,
    }
  },
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <button class="back-btn" @click="goBack">← 返回</button>
      <div class="header-info">
        <h1 class="page-title">链路详情</h1>
        <span class="trace-id-label mono">{{ traceId }}</span>
      </div>
    </div>

    <div v-if="loading" class="loading-state"><span class="spinner"></span><span>加载中...</span></div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <div v-else-if="sections" class="detail-layout">
      <div class="summary-bar">
        <div class="stat-card"><div class="stat-val">{{ sections.summary.total }}</div><div class="stat-label">条目数</div></div>
        <div class="stat-card success"><div class="stat-val">{{ sections.summary.success }}</div><div class="stat-label">成功</div></div>
        <div class="stat-card warning"><div class="stat-val">{{ sections.summary.running }}</div><div class="stat-label">处理中</div></div>
        <div class="stat-card error"><div class="stat-val">{{ sections.summary.failed }}</div><div class="stat-label">失败</div></div>
        <div class="stat-card"><div class="stat-val">{{ sections.summary.totalTokens }}</div><div class="stat-label">Tokens</div></div>
      </div>

      <div class="overview-card">
        <h2 class="section-title">基础信息</h2>
        <div class="overview-grid">
          <div v-for="entry in sections.overview" :key="entry[0]" class="overview-item">
            <div class="overview-label">{{ entry[0] }}</div>
            <div class="overview-value mono">{{ entry[1] }}</div>
          </div>
        </div>
      </div>

      <div class="content-grid">
        <div class="panel">
          <h2 class="section-title">请求内容</h2>
          <pre class="panel-pre">{{ sections.requestMessage }}</pre>
        </div>
        <div class="panel">
          <h2 class="section-title">题目快照</h2>
          <JsonTreeViewer :value="sections.questionSnapshot" class="panel-pre" />
        </div>
      </div>

      <div class="items-panel">
        <div class="items-header">
          <h2 class="section-title">Trace Items</h2>
          <div class="items-actions">
            <button class="items-action-btn" type="button" @click="toggleExpandAll">
              {{ allExpanded ? '全部收起' : '全部展开' }}
            </button>
            <button class="items-action-btn" type="button" @click="failedOnly = !failedOnly">
              {{ failedOnly ? '显示全部' : '仅看失败' }}
            </button>
          </div>
        </div>
        <div v-if="displayItems.length === 0" class="empty-state">暂无 trace_item 数据</div>
        <div v-else class="item-list">
          <div class="timeline-axis-row timeline-grid-row">
            <div class="timeline-axis-spacer" aria-hidden="true"></div>
            <div class="timeline-axis-cell">
              <div class="timeline-axis-track">
                <div
                  v-for="tick in timelineTicks"
                  :key="tick.key"
                  class="timeline-axis-tick"
                  :style="{ left: `${tick.leftPct}%` }"
                >
                  <span class="timeline-axis-label mono">{{ tick.label }}</span>
                </div>
              </div>
            </div>
            <div class="timeline-axis-spacer" aria-hidden="true"></div>
          </div>

          <div v-for="item in displayItems" :key="item.id" class="item-row" :class="`item-${item.statusClass}`">
            <button
              class="row-main timeline-grid-row"
              type="button"
              :aria-expanded="item.expanded"
              :aria-controls="`trace-item-detail-${item.id}`"
              @click="toggleExpanded(item.id)"
            >
              <div class="row-title-cell">
                <span class="item-title">{{ item.title }}</span>
                <div class="item-subtitle">{{ item.subtitle }}</div>
              </div>
              <div class="row-track-cell">
                <div class="timeline-track">
                  <div
                    class="timeline-range"
                    :class="`timeline-${item.statusClass}`"
                    :style="{ left: `${item.leftPct}%`, width: `${item.widthPct}%` }"
                  ></div>
                </div>
              </div>
              <div class="row-duration-cell">
                <span class="status-badge" :class="`status-${item.statusClass}`">{{ item.statusLabel }}</span>
                <span class="duration-text mono">{{ item.durationText }}</span>
                <span class="expand-indicator">{{ item.expanded ? '▲' : '▼' }}</span>
              </div>
            </button>

            <div v-if="item.errorMessage" class="error-banner">{{ item.errorMessage }}</div>

            <div v-if="item.expanded" :id="`trace-item-detail-${item.id}`" class="item-detail">
              <div class="meta-grid">
                <div><span class="meta-label">工具</span><span class="meta-value">{{ item.toolName || '-' }}</span></div>
                <div><span class="meta-label">输入摘要</span><span class="meta-value">{{ item.inputSummary || '-' }}</span></div>
                <div><span class="meta-label">输出摘要</span><span class="meta-value">{{ item.outputSummary || '-' }}</span></div>
                <div><span class="meta-label">Token</span><span class="meta-value">{{ item.tokenText }}</span></div>
                <div><span class="meta-label">开始时间</span><span class="meta-value mono">{{ formatTimestamp(item.startTimestamp) }}</span></div>
                <div><span class="meta-label">结束时间</span><span class="meta-value mono">{{ formatTimestamp(item.endTimestamp) }}</span></div>
              </div>

              <div class="payload-grid">
                <div class="payload-panel">
                  <div class="payload-label">Input Payload</div>
                  <JsonTreeViewer :value="item.inputPayload" class="payload-pre" />
                </div>
                <div class="payload-panel">
                  <div class="payload-label">Output Payload</div>
                  <JsonTreeViewer :value="item.outputPayload" class="payload-pre" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { padding: 2rem; max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 1.5rem; margin-bottom: 1.5rem; }
.back-btn { background: var(--card); border: 1px solid var(--border); color: var(--text-dim); padding: 0.4rem 0.9rem; border-radius: 6px; cursor: pointer; }
.back-btn:focus-visible, .row-main:focus-visible { outline: none; box-shadow: 0 0 0 3px var(--primary-ring); }
.header-info { display: flex; flex-direction: column; gap: 0.25rem; }
.page-title { font-family: var(--font-heading); font-size: 1.5rem; font-weight: 700; }
.trace-id-label { font-size: 0.75rem; color: var(--text-muted); }
.loading-state { display: flex; align-items: center; gap: 0.75rem; color: var(--text-muted); padding: 3rem; justify-content: center; }
.spinner { width: 20px; height: 20px; border: 2px solid var(--border); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.error-msg { color: var(--error); background: var(--error-dim); border: 1px solid var(--error); padding: 0.75rem 1rem; border-radius: 8px; }
.detail-layout { display: flex; flex-direction: column; gap: 1rem; }
.summary-bar { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.stat-card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 0.75rem 1rem; min-width: 110px; }
.stat-card.success { border-color: rgba(34,197,94,0.3); }
.stat-card.warning { border-color: rgba(251,191,36,0.3); }
.stat-card.error { border-color: rgba(244,63,94,0.3); }
.stat-val { font-family: var(--font-heading); font-size: 1.4rem; font-weight: 700; }
.stat-label { font-size: 0.72rem; color: var(--text-muted); margin-top: 0.15rem; }
.overview-card, .panel, .items-panel { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 1rem; }
.section-title { font-size: 1rem; font-weight: 700; margin-bottom: 0.75rem; }
.items-header { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; margin-bottom: 0.75rem; }
.items-actions { display: inline-flex; gap: 0.45rem; }
.items-action-btn { min-height: 34px; padding: 0 0.7rem; border-radius: 8px; border: 1px solid var(--border); background: var(--surface); color: var(--text-dim); font-size: 0.8rem; font-weight: 600; cursor: pointer; }
.items-action-btn:hover { border-color: var(--primary); color: var(--primary); }
.items-action-btn:focus-visible { outline: none; box-shadow: 0 0 0 3px var(--primary-ring); }
.overview-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.75rem; }
.overview-item { background: var(--surface); border-radius: 8px; padding: 0.75rem; }
.overview-label, .meta-label, .payload-label { font-size: 0.72rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
.overview-value, .meta-value { margin-top: 0.35rem; color: var(--text-dim); }
.content-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1rem; }
.panel-pre, .payload-pre { background: var(--surface); border-radius: 8px; padding: 0.9rem; white-space: pre-wrap; word-break: break-word; font-family: var(--font-mono); font-size: 0.8rem; }
.item-list { display: flex; flex-direction: column; gap: 0.75rem; }
.timeline-grid-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) minmax(320px, 1.8fr) minmax(180px, 0.8fr);
  align-items: center;
  gap: 0.9rem;
}
.timeline-axis-row { padding: 0.9rem 1rem 0.35rem; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
.timeline-axis-spacer { min-width: 0; }
.timeline-axis-cell { min-width: 0; }
.timeline-axis-track { position: relative; height: 28px; }
.timeline-axis-tick { position: absolute; top: 0; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; gap: 0.2rem; }
.timeline-axis-tick::before { content: ''; width: 1px; height: 8px; background: var(--border); }
.timeline-axis-label { font-size: 0.68rem; color: var(--text-muted); white-space: nowrap; }
.item-row { border: 1px solid var(--border); border-left-width: 4px; border-radius: 10px; background: rgba(255,255,255,0.01); overflow: hidden; }
.item-running { border-left-color: var(--warning); }
.item-success { border-left-color: var(--success); }
.item-error { border-left-color: var(--error); }
.row-main { width: 100%; border: 0; background: transparent; color: inherit; text-align: left; align-items: center; padding: 0.85rem 1rem; cursor: pointer; }
.row-main:hover { background: rgba(255,255,255,0.02); }
.row-title-cell { min-width: 0; }
.item-title { font-size: 0.96rem; font-weight: 700; }
.item-subtitle { margin-top: 0.2rem; color: var(--text-muted); font-size: 0.8rem; }
.row-track-cell { min-width: 0; }
.timeline-track { position: relative; width: 100%; height: 10px; border-radius: 999px; background: rgba(148, 163, 184, 0.2); overflow: hidden; }
.timeline-range { position: absolute; top: 0; height: 100%; border-radius: 999px; min-width: 4px; }
.timeline-running { background: rgba(251, 191, 36, 0.9); }
.timeline-success { background: rgba(34, 197, 94, 0.9); }
.timeline-error { background: rgba(244, 63, 94, 0.9); }
.row-duration-cell { display: flex; align-items: center; justify-content: flex-end; gap: 0.45rem; }
.duration-text { font-size: 0.78rem; color: var(--primary); }
.expand-indicator { font-size: 0.65rem; color: var(--text-muted); }
.status-badge { display: inline-flex; align-items: center; min-height: 24px; padding: 0 0.55rem; border-radius: 999px; border: 1px solid var(--border); background: var(--surface); font-size: 0.74rem; font-weight: 600; color: var(--text-dim); }
.status-badge.status-success { border-color: rgba(34, 197, 94, 0.45); color: var(--success); background: rgba(34, 197, 94, 0.12); }
.status-badge.status-running { border-color: rgba(245, 158, 11, 0.45); color: var(--warning); background: rgba(245, 158, 11, 0.12); }
.status-badge.status-error { border-color: rgba(251, 113, 133, 0.45); color: var(--error); background: rgba(251, 113, 133, 0.14); }
.item-detail { border-top: 1px solid var(--border); padding: 0.85rem 1rem 1rem; }
.meta-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.75rem; }
.meta-grid > div { background: var(--surface); border-radius: 8px; padding: 0.7rem; display: flex; flex-direction: column; }
.error-banner { background: var(--error-dim); color: var(--error); border-top: 1px solid rgba(244,63,94,0.3); padding: 0.75rem 1rem; }
.payload-grid { margin-top: 0.85rem; display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 0.75rem; }
.payload-panel { display: flex; flex-direction: column; gap: 0.5rem; }
.empty-state { color: var(--text-muted); padding: 1.5rem 0; }
.mono { font-family: var(--font-mono); }

@media (max-width: 960px) {
  .page { padding: 1rem; }
  .items-header { flex-direction: column; align-items: flex-start; }
  .items-actions { width: 100%; }
  .items-action-btn { flex: 1; }
  .timeline-grid-row { grid-template-columns: 1fr; gap: 0.5rem; }
  .timeline-axis-row { display: none; }
  .row-duration-cell { justify-content: flex-start; }
}
</style>
