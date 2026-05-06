<script lang="ts">
import { computed, defineComponent, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { pageTraces } from '@/api/trace'
import { getTraceStatusLabel, type TraceListItem, type TracePageQuery, type TraceStatus } from '@/types/trace'

interface TraceListRowView {
  id: string
  traceId: string
  conversationId: string
  userId: string
  requestMessage: string
  currentAlgorithmQuestionId: string
  statusLabel: string
  statusTone: 'running' | 'success' | 'error'
  errorMessage: string | null
  hasError: boolean
  createTime: string
  updateTime: string
}

interface TraceListSummary {
  total: number
  success: number
  failed: number
  running: number
}

// 清理列表查询参数，只保留后端支持的字段。
export function normalizeTraceListQuery(query: TracePageQuery): TracePageQuery {
  return {
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    traceId: query.traceId?.trim() || undefined,
    conversationId: query.conversationId?.trim() || undefined,
    requestMessage: query.requestMessage?.trim() || undefined,
    status: query.status,
  }
}

// 将 trace 状态映射为明确的展示样式。
export function getTraceStatusTone(status: TraceStatus, errorMessage: string | null): 'running' | 'success' | 'error' {
  if (status === 'FAILED' || Boolean(errorMessage)) return 'error'
  if (status === 'SUCCESS') return 'success'
  return 'running'
}

// 构建 trace 详情页路径。
export function buildTraceDetailPath(traceId: string): string {
  return `/trace/${encodeURIComponent(traceId)}`
}

function getLocalTimezoneOffset(): string {
  const offset = -new Date().getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const absoluteOffset = Math.abs(offset)
  const hour = String(Math.floor(absoluteOffset / 60)).padStart(2, '0')
  const minute = String(absoluteOffset % 60).padStart(2, '0')
  return `${sign}${hour}:${minute}`
}

// 统一格式化后端 LocalDateTime，避免浏览器按 UTC 解析导致时间偏移。
export function formatTraceDateTime(time: string): string {
  if (!time) {
    return '-'
  }

  const normalized = time.replace(' ', 'T')
  const hasTimezone = /[Z+\-]\d{2}:?\d{2}$/.test(normalized) || normalized.endsWith('Z')
  const dateValue = hasTimezone ? normalized : `${normalized}${getLocalTimezoneOffset()}`
  const date = new Date(dateValue)

  if (Number.isNaN(date.getTime())) {
    return time
  }

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

// 将 trace 列表项格式化为时间线展示数据。
export function formatTraceListRow(item: TraceListItem): TraceListRowView {
  const statusTone = getTraceStatusTone(item.status, item.errorMessage)

  return {
    id: `${item.id}`,
    traceId: item.traceId,
    conversationId: item.conversationId || '-',
    userId: item.userId === null ? '-' : `${item.userId}`,
    requestMessage: item.requestMessage || '-',
    currentAlgorithmQuestionId: item.currentAlgorithmQuestionId === null ? '-' : `${item.currentAlgorithmQuestionId}`,
    statusLabel: getTraceStatusLabel(item.status),
    statusTone,
    errorMessage: item.errorMessage,
    hasError: statusTone === 'error',
    createTime: item.createTime,
    updateTime: item.updateTime,
  }
}

// 构建列表页时间线渲染模型，便于测试页面展示意图。
export function buildTraceListRenderModel(items: TraceListItem[]): TraceListRowView[] {
  return items.map(formatTraceListRow)
}

// 汇总列表状态，方便在页面顶部快速查看链路分布。
export function summarizeTraceList(items: TraceListItem[]): TraceListSummary {
  return items.reduce<TraceListSummary>((summary, item) => {
    summary.total += 1
    if (item.status === 'SUCCESS') summary.success += 1
    else if (item.status === 'FAILED') summary.failed += 1
    else summary.running += 1
    return summary
  }, {
    total: 0,
    success: 0,
    failed: 0,
    running: 0,
  })
}

export default defineComponent({
  name: 'SpanListView',
  setup() {
    const router = useRouter()
    const loading = ref(false)
    const error = ref('')
    const records = ref<TraceListItem[]>([])
    const total = ref(0)
    const totalPages = ref(0)

    // 使用后端支持的 trace 查询字段构建筛选表单。
    const query = reactive<TracePageQuery>({
      pageNum: 1,
      pageSize: 20,
      traceId: '',
      conversationId: '',
      requestMessage: '',
      status: undefined,
    })

    // 根据当前筛选条件拉取 trace 列表。
    async function fetchData() {
      loading.value = true
      error.value = ''
      try {
        const result = await pageTraces(normalizeTraceListQuery(query))
        records.value = result.records || []
        total.value = result.total || 0
        totalPages.value = result.pages || 0
      } catch (e: any) {
        error.value = e.message || '加载失败'
        records.value = []
        total.value = 0
        totalPages.value = 0
      } finally {
        loading.value = false
      }
    }

    // 重新按第一页执行筛选。
    function search() {
      query.pageNum = 1
      void fetchData()
    }

    // 重置列表筛选条件。
    function reset() {
      query.pageNum = 1
      query.traceId = ''
      query.conversationId = ''
      query.requestMessage = ''
      query.status = undefined
      void fetchData()
    }

    // 切换分页并重新拉取数据。
    function goPage(pageNum: number) {
      query.pageNum = pageNum
      void fetchData()
    }

    // 打开指定 trace 的详情页。
    function goTrace(traceId: string) {
      void router.push(buildTraceDetailPath(traceId))
    }

    // 将列表数据映射为展示行。
    const rows = computed(() => buildTraceListRenderModel(records.value))

    // 统计当前列表中的状态分布。
    const summary = computed(() => summarizeTraceList(records.value))

    // 首次进入页面时自动加载列表。
    onMounted(() => {
      void fetchData()
    })

    return {
      error,
      formatTraceDateTime,
      goPage,
      goTrace,
      loading,
      query,
      reset,
      rows,
      search,
      summary,
      total,
      totalPages,
      statusOptions: [
        { label: '全部状态', value: undefined },
        { label: '执行中', value: 'RUNNING' },
        { label: '成功', value: 'SUCCESS' },
        { label: '失败', value: 'FAILED' },
      ] as Array<{ label: string; value: TraceStatus | undefined }>,
    }
  },
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">链路追踪</h1>
        <p class="page-subtitle">按 trace 维度查看请求、会话、题目上下文与执行状态。</p>
      </div>
      <span class="total-badge">共 {{ total }} 条</span>
    </div>

    <div class="summary-bar">
      <div class="stat-card">
        <div class="stat-val">{{ summary.total }}</div>
        <div class="stat-label">当前页</div>
      </div>
      <div class="stat-card success">
        <div class="stat-val">{{ summary.success }}</div>
        <div class="stat-label">当前页成功</div>
      </div>
      <div class="stat-card warning">
        <div class="stat-val">{{ summary.running }}</div>
        <div class="stat-label">当前页处理中</div>
      </div>
      <div class="stat-card error" :class="{ inactive: summary.failed === 0 }">
        <div class="stat-val">{{ summary.failed }}</div>
        <div class="stat-label">当前页失败</div>
      </div>
    </div>

    <form class="filter-bar" @submit.prevent="search">
      <div class="filter-field">
        <label for="trace-id-filter">Trace ID</label>
        <input id="trace-id-filter" v-model="query.traceId" class="filter-input" placeholder="输入 Trace ID" />
      </div>
      <div class="filter-field">
        <label for="conversation-id-filter">Conversation ID</label>
        <input id="conversation-id-filter" v-model="query.conversationId" class="filter-input" placeholder="输入会话 ID" />
      </div>
      <div class="filter-field filter-field-grow">
        <label for="request-message-filter">请求内容</label>
        <input id="request-message-filter" v-model="query.requestMessage" class="filter-input" placeholder="输入请求关键字" />
      </div>
      <div class="filter-field">
        <label for="status-filter">状态</label>
        <select id="status-filter" v-model="query.status" class="filter-select">
          <option v-for="option in statusOptions" :key="option.label" :value="option.value">{{ option.label }}</option>
        </select>
      </div>
      <div class="filter-actions">
        <button class="btn-primary" type="submit">查询</button>
        <button class="btn-ghost" type="button" @click="reset">重置</button>
      </div>
    </form>

    <div v-if="error" class="error-msg">{{ error }}</div>

    <div class="timeline-wrap">
      <div v-if="loading" class="loading-overlay">
        <span class="spinner"></span>
      </div>
      <div v-if="rows.length === 0 && !loading" class="empty-row">暂无数据</div>
      <div v-else class="timeline-list">
        <button
          v-for="row in rows"
          :key="row.id"
          class="timeline-card"
          :class="[`timeline-${row.statusTone}`]"
          type="button"
          :aria-label="`查看 ${row.traceId} 链路详情`"
          @click="goTrace(row.traceId)"
        >
          <div class="timeline-head">
            <div class="timeline-marker"></div>
            <div class="timeline-main">
              <div class="timeline-title-row">
                <div>
                  <h2 class="timeline-title mono">{{ row.traceId }}</h2>
                  <div class="timeline-subtitle">会话 {{ row.conversationId }} · 用户 {{ row.userId }} · 题目 {{ row.currentAlgorithmQuestionId }}</div>
                </div>
                <div class="timeline-actions">
                  <span class="status-badge" :class="`status-${row.statusTone}`">{{ row.statusLabel }}</span>
                </div>
              </div>
              <div class="timeline-meta-grid">
                <div class="meta-item">
                  <div class="meta-label">请求内容</div>
                  <div class="meta-value request-message">{{ row.requestMessage }}</div>
                </div>
                <div class="meta-item">
                  <div class="meta-label">创建时间</div>
                  <div class="meta-value mono">{{ formatTraceDateTime(row.createTime) }}</div>
                </div>
                <div class="meta-item">
                  <div class="meta-label">更新时间</div>
                  <div class="meta-value mono">{{ formatTraceDateTime(row.updateTime) }}</div>
                </div>
                <div class="meta-item">
                  <div class="meta-label">错误信息</div>
                  <div class="meta-value" :class="{ 'error-text': row.errorMessage }">{{ row.errorMessage || '-' }}</div>
                </div>
              </div>
            </div>
          </div>
        </button>
      </div>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button class="page-btn" :disabled="query.pageNum <= 1" aria-label="上一页" @click="goPage(query.pageNum - 1)">‹</button>
      <span class="page-info">{{ query.pageNum }} / {{ totalPages }}</span>
      <button class="page-btn" :disabled="query.pageNum >= totalPages" aria-label="下一页" @click="goPage(query.pageNum + 1)">›</button>
    </div>
  </div>
</template>

<style scoped>
.page { padding: 2rem; max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; margin-bottom: 1.5rem; }
.page-title { font-family: var(--font-heading); font-size: 1.75rem; font-weight: 700; }
.page-subtitle { margin-top: 0.35rem; color: var(--text-muted); font-size: 0.9rem; }
.total-badge { font-size: 0.8rem; color: var(--text-muted); background: var(--card); border: 1px solid var(--border); padding: 0.3rem 0.7rem; border-radius: 999px; }
.summary-bar { display: flex; gap: 0.75rem; margin-bottom: 1rem; flex-wrap: wrap; }
.stat-card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 0.8rem 1rem; min-width: 110px; }
.stat-card.success { border-color: rgba(34,197,94,0.3); }
.stat-card.warning { border-color: rgba(251,191,36,0.3); }
.stat-card.error { border-color: rgba(244,63,94,0.3); }
.stat-card.inactive { opacity: 0.7; }
.stat-val { font-family: var(--font-heading); font-size: 1.4rem; font-weight: 700; }
.stat-label { font-size: 0.75rem; color: var(--text-muted); margin-top: 0.1rem; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-bottom: 1rem; align-items: flex-end; }
.filter-field { display: flex; flex-direction: column; gap: 0.32rem; min-width: 160px; }
.filter-field-grow { flex: 1; min-width: 220px; }
.filter-field label { font-size: 0.74rem; color: var(--text-muted); letter-spacing: 0.03em; text-transform: uppercase; }
.filter-input, .filter-select { background: var(--card); border: 1px solid var(--border); color: var(--text); min-height: 38px; padding: 0 0.72rem; border-radius: 8px; font-size: 0.84rem; }
.filter-input:focus-visible, .filter-select:focus-visible { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-ring); }
.filter-select { min-width: 120px; }
.filter-actions { display: inline-flex; gap: 0.45rem; }
.btn-primary { min-height: 38px; background: var(--primary); color: #fff; border: none; padding: 0 1rem; border-radius: 8px; cursor: pointer; font-weight: 600; }
.btn-primary:hover { background: var(--primary-dark); }
.btn-primary:focus-visible, .btn-ghost:focus-visible, .page-btn:focus-visible { outline: none; box-shadow: 0 0 0 3px var(--primary-ring); }
.btn-ghost { min-height: 38px; background: transparent; color: var(--text-dim); border: 1px solid var(--border); padding: 0 1rem; border-radius: 8px; cursor: pointer; }
.btn-ghost:hover { border-color: var(--primary); color: var(--primary); }
.error-msg { color: var(--error); background: var(--error-dim); border: 1px solid var(--error); padding: 0.75rem 1rem; border-radius: 8px; margin-bottom: 1rem; }
.timeline-wrap { position: relative; }
.loading-overlay { position: absolute; inset: 0; background: rgba(8,12,20,0.45); display: flex; align-items: center; justify-content: center; z-index: 10; border-radius: 12px; }
.spinner { width: 24px; height: 24px; border: 2px solid var(--border); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.timeline-list { display: flex; flex-direction: column; gap: 1rem; }
.timeline-card { position: relative; width: 100%; border: 1px solid var(--border); border-radius: 12px; overflow: hidden; cursor: pointer; background: var(--card); color: inherit; text-align: left; }
.timeline-card::before { content: ''; position: absolute; left: 24px; top: 0; bottom: 0; width: 2px; background: rgba(79,142,247,0.14); }
.timeline-card:focus-visible { outline: none; box-shadow: 0 0 0 3px var(--primary-ring); }
.timeline-card:hover { border-color: var(--primary); box-shadow: var(--shadow-sm); }
.timeline-running { border-color: rgba(251,191,36,0.35); }
.timeline-success { border-color: rgba(34,197,94,0.35); }
.timeline-error { border-color: rgba(244,63,94,0.35); }
.timeline-head { position: relative; display: flex; gap: 1rem; padding: 1.1rem 1.1rem 1.1rem 3rem; }
.timeline-marker { position: absolute; left: 16px; top: 1.4rem; width: 18px; height: 18px; border-radius: 50%; background: var(--primary); border: 4px solid rgba(79,142,247,0.18); }
.timeline-main { width: 100%; display: flex; flex-direction: column; gap: 0.85rem; }
.timeline-title-row { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }
.timeline-title { font-size: 1rem; font-weight: 700; }
.timeline-subtitle { margin-top: 0.3rem; color: var(--text-muted); font-size: 0.82rem; }
.timeline-actions { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; justify-content: flex-end; }
.timeline-meta-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.75rem; }
.meta-item { background: var(--surface); border-radius: 8px; padding: 0.75rem; }
.meta-label { font-size: 0.72rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
.meta-value { margin-top: 0.3rem; color: var(--text-dim); white-space: pre-wrap; word-break: break-word; }
.request-message { max-height: 4.5em; overflow: hidden; }
.error-text { color: var(--error); }
.empty-row { text-align: center; color: var(--text-muted); padding: 3rem; background: var(--card); border: 1px solid var(--border); border-radius: 12px; }
.status-badge { display: inline-flex; align-items: center; padding: 0.2rem 0.55rem; border-radius: 999px; }
.status-badge.status-running { background: rgba(251,191,36,0.15); color: #f59e0b; }
.status-badge.status-success { background: rgba(34,197,94,0.15); color: var(--success); }
.status-badge.status-error { background: rgba(244,63,94,0.15); color: var(--error); }
.pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1.5rem; }
.page-btn { background: var(--card); border: 1px solid var(--border); color: var(--text); width: 32px; height: 32px; border-radius: 6px; cursor: pointer; }
.page-info { font-size: 0.875rem; color: var(--text-dim); font-family: var(--font-mono); }
.mono { font-family: var(--font-mono); font-size: 0.8rem; }

@media (max-width: 768px) {
  .page { padding: 1rem; }
  .filter-field, .filter-field-grow { min-width: 100%; }
  .filter-actions { width: 100%; }
  .btn-primary, .btn-ghost { flex: 1; }
}
</style>
