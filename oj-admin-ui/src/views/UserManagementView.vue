<template>
  <main class="page">
    <header class="header">
      <div>
        <h1 class="page-title">用户登录日志</h1>
        <p class="subtitle">查看用户登录记录与访问轨迹</p>
      </div>
      <button class="btn btn-light" :disabled="loading" @click="loadLoginLogs">刷新日志</button>
    </header>

    <section class="panel content-panel">
      <div v-if="loading" class="state state-loading">
        <span class="state-spinner" aria-hidden="true" />
        <div>
          <p class="state-title">正在加载日志</p>
          <p class="state-desc">请稍候，系统正在拉取最新记录。</p>
        </div>
      </div>

      <div v-else-if="errorMessage" class="state state-error state-with-action">
        <div>
          <p class="state-title">加载失败</p>
          <p class="state-desc text-ellipsis-2" :title="errorMessage">{{ errorMessage }}</p>
        </div>
        <button class="btn btn-light" @click="loadLoginLogs">重试</button>
      </div>

      <div v-else-if="logs.length === 0" class="state state-empty" :class="{ 'state-with-action': isOutOfRangeEmpty }">
        <span class="state-empty-icon" aria-hidden="true" />
        <div>
          <p class="state-title">{{ isOutOfRangeEmpty ? '当前页超出可用范围' : '暂无登录日志' }}</p>
          <p class="state-desc">
            {{ isOutOfRangeEmpty ? '日志总量已变化，当前页没有数据，请回退到可用页。' : '当前没有可展示的登录日志。' }}
          </p>
        </div>
        <button v-if="isOutOfRangeEmpty" class="btn btn-light" :disabled="loading" @click="backToLastPage">回到最后一页</button>
      </div>

      <template v-else>
        <div class="table-scroll scroll-container">
          <table class="table table-grid table-logs">
            <thead>
              <tr>
                <th class="cell-number">日志 ID</th>
                <th class="cell-number">用户 ID</th>
                <th class="cell-name">用户名</th>
                <th class="cell-ip">登录 IP</th>
                <th class="cell-date">登录时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in logs" :key="item.id">
                <td class="cell-number">{{ item.id }}</td>
                <td class="cell-number">{{ item.userId }}</td>
                <td class="cell-name">
                  <span class="text-ellipsis cell-ellipsis" :title="item.username || '-'">
                    {{ item.username || '-' }}
                  </span>
                </td>
                <td class="cell-ip">
                  <span class="text-ellipsis cell-ellipsis" :title="item.loginIp || '-'">
                    {{ item.loginIp || '-' }}
                  </span>
                </td>
                <td class="cell-date">{{ formatDateTime(item.loginTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button class="btn btn-light" :disabled="loading || current <= 1" @click="changePage(current - 1)">
            上一页
          </button>
          <span class="pagination-summary">第 {{ current }} / {{ totalPages }} 页，共 {{ total }} 条</span>
          <button class="btn btn-light" :disabled="loading || current >= totalPages" @click="changePage(current + 1)">
            下一页
          </button>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminUserApi, type AdminLoginLogItem } from '@/api/adminUser'

const DEFAULT_PAGE_SIZE = 10
const current = ref(1)
const size = ref(10)
const total = ref(0)
const totalPages = ref(1)
const logs = ref<AdminLoginLogItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const isOutOfRangeEmpty = computed(() => total.value > 0 && logs.value.length === 0)

// 缁熶竴閿欒鏂囨
const getErrorMessage = (error: unknown) => {
  return error instanceof Error ? error.message : '鎿嶄綔澶辫触'
}

// 缁熶竴鏃ユ湡灞曠ず鏍煎紡
const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ')
}

// 统一归一化正整数分页参数，避免后端异常值污染页面状态
const normalizePositiveInteger = (value: number, fallback: number) => {
  if (!Number.isFinite(value) || value <= 0) {
    return fallback
  }
  return Math.floor(value)
}

// 鍔犺浇鐧诲綍鏃ュ織鍒嗛〉鍐呴儴瀹炵幇锛屾敮鎸佹帶鍒舵槸鍚﹂噸璇曡秺鐣岄〉
const loadLoginLogsInternal = async (allowOverflowRetry: boolean) => {
  loading.value = true
  errorMessage.value = ''
  try {
    const requestCurrent = normalizePositiveInteger(current.value, 1)
    const requestSize = normalizePositiveInteger(size.value, DEFAULT_PAGE_SIZE)
    current.value = requestCurrent
    size.value = requestSize

    const page = await adminUserApi.pageLoginLogs({
      current: requestCurrent,
      size: requestSize,
    })
    const safeSize = normalizePositiveInteger(page.size, requestSize)
    const safeTotal = Number.isFinite(page.total) && page.total > 0 ? Math.floor(page.total) : 0
    const safeCurrent = normalizePositiveInteger(page.current, requestCurrent)
    const safeTotalPages = Math.max(1, Math.ceil(safeTotal / safeSize))
    const safeRecords = Array.isArray(page.records) ? page.records : []

    const isOutOfRangePage =
      safeTotal > 0 && safeRecords.length === 0 && (safeCurrent > safeTotalPages || requestCurrent > safeTotalPages)
    if (isOutOfRangePage && allowOverflowRetry) {
      current.value = safeTotalPages
      await loadLoginLogsInternal(false)
      return
    }

    logs.value = safeRecords
    total.value = safeTotal
    size.value = safeSize
    totalPages.value = safeTotalPages
    current.value = Math.min(safeCurrent, safeTotalPages)
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
    logs.value = []
    total.value = 0
    current.value = 1
    totalPages.value = 1
    size.value = normalizePositiveInteger(size.value, DEFAULT_PAGE_SIZE)
  } finally {
    loading.value = false
  }
}

// 鍔犺浇鐧诲綍鏃ュ織鍒嗛〉鍏紑鍏ュ彛
const loadLoginLogs = async () => {
  await loadLoginLogsInternal(true)
}

// 瓒婄晫绌洪〉鏃跺洖閫€鍒版渶鍚庝竴椤靛苟閲嶈浇
const backToLastPage = async () => {
  current.value = Math.max(1, totalPages.value)
  await loadLoginLogsInternal(false)
}

// 鍒囨崲鍒嗛〉骞跺鐢ㄥ姞杞介€昏緫
const changePage = async (page: number) => {
  const normalizedPage = normalizePositiveInteger(page, current.value)
  const nextPage = Math.min(Math.max(normalizedPage, 1), totalPages.value)
  if (nextPage === current.value) {
    return
  }
  current.value = nextPage
  await loadLoginLogs()
}

onMounted(async () => {
  await loadLoginLogs()
})
</script>

<style scoped>
.page {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-base);
}

.page-title {
  margin: 0;
  font-family: var(--font-family-heading);
  font-size: 23px;
  line-height: 1.2;
  color: #123f8e;
}

.subtitle {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.45;
}

.content-panel {
  padding: 18px 16px 16px;
  display: grid;
  gap: 12px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.table-scroll {
  width: 100%;
  max-width: 100%;
  max-height: 520px;
  overflow-x: hidden;
  overflow-y: auto;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
}

.table-grid {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
  border: none;
  border-radius: 0;
}

.table-grid thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--bg-muted);
  padding: 12px 10px;
  font-weight: 700;
}

.table-grid th,
.table-grid td {
  vertical-align: middle;
  min-width: 0;
}

.table-grid td {
  padding: 10px;
  border-top: 1px solid var(--border-base);
}

.table-grid tbody tr:nth-child(even) {
  background: #fbfdff;
}

.table-grid tbody tr:hover {
  background: var(--bg-soft);
}

.cell-number {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
  width: 14%;
  white-space: nowrap;
}

.cell-date {
  text-align: center !important;
  font-variant-numeric: tabular-nums;
  width: 24%;
  white-space: nowrap;
}

.cell-name {
  width: 24%;
}

.cell-ip {
  width: 24%;
}

.cell-ellipsis {
  display: inline-block;
  width: 100%;
  max-width: none;
  vertical-align: middle;
  white-space: nowrap;
}

.state {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-height: 72px;
  padding: 14px 16px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-sm);
  background: var(--bg-soft);
}

.state-with-action {
  justify-content: space-between;
}

.state-title {
  margin: 0;
  color: var(--text-primary);
  font-weight: 700;
  font-family: var(--font-family-heading);
  letter-spacing: 0.01em;
}

.state-desc {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.state-loading,
.state-empty {
  background: var(--bg-soft);
}

.state-error {
  background: #fef2f2;
  border-color: #fecaca;
}

.state-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--border-base);
  border-top-color: var(--brand);
  border-radius: 50%;
  flex: 0 0 auto;
  animation: spin 0.9s linear infinite;
}

.state-empty-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px dashed var(--border-strong);
  background: var(--bg-muted);
  flex: 0 0 auto;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px solid var(--border-base);
}

.pagination-summary {
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.btn {
  min-height: 38px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .page {
    padding: 16px;
  }

  .header {
    flex-direction: column;
    align-items: stretch;
  }

  .page-title {
    font-size: 20px;
  }

  .cell-ellipsis {
    white-space: normal;
    word-break: break-word;
  }

  .state-with-action {
    flex-direction: column;
    align-items: stretch;
  }

  .pagination {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>


