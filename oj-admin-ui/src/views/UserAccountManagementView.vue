<template>
  <main class="page">
    <header class="header">
      <div>
        <h1 class="page-title">用户账户管理</h1>
        <p class="subtitle">维护账户状态、有效期与试用次数</p>
      </div>
      <button class="btn btn-light" :disabled="loading || isDisableOperating" @click="loadUsers()">刷新用户</button>
    </header>

    <section class="panel content-panel">
      <p v-if="noticeMessage" class="feedback-banner feedback-success">{{ noticeMessage }}</p>
      <p v-if="disableErrorMessage" class="feedback-banner feedback-error">{{ disableErrorMessage }}</p>

      <div v-if="loading" class="state state-loading">
        <span class="state-spinner" aria-hidden="true" />
        <div>
          <p class="state-title">正在加载用户数据</p>
          <p class="state-desc">请稍候，系统正在同步最新账户信息。</p>
        </div>
      </div>

      <div v-else-if="loadErrorMessage" class="state state-error state-with-action">
        <div>
          <p class="state-title">加载失败</p>
          <p class="state-desc text-ellipsis-2" :title="loadErrorMessage">{{ loadErrorMessage }}</p>
        </div>
        <button class="btn btn-light" :disabled="isDisableOperating" @click="loadUsers()">重试</button>
      </div>

      <div v-else-if="users.length === 0" class="state state-empty">
        <span class="state-empty-icon" aria-hidden="true" />
        <div>
          <p class="state-title">暂无用户数据</p>
          <p class="state-desc">当前分页没有可展示的账户记录。</p>
        </div>
      </div>

      <template v-else>
        <div class="table-scroll scroll-container">
          <table class="table table-grid">
            <thead>
              <tr>
                <th class="cell-number">ID</th>
                <th>用户名</th>
                <th>类型</th>
                <th>需密码</th>
                <th>状态</th>
                <th>有效期</th>
                <th class="cell-number">剩余次数</th>
                <th class="cell-date">创建时间</th>
                <th class="cell-date">更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in users" :key="item.id">
                <td class="cell-number">{{ item.id }}</td>
                <td>
                  <span class="text-ellipsis cell-ellipsis" :title="item.username || '-'">
                    {{ item.username || '-' }}
                  </span>
                </td>
                <td>
                  <span class="status-chip" :class="`status-chip-${resolveUserTypeMeta(item.userType).tone}`">
                    <span class="status-dot" aria-hidden="true" />
                    {{ resolveUserTypeMeta(item.userType).label }}
                  </span>
                </td>
                <td>
                  <span class="status-chip" :class="`status-chip-${resolveNeedPasswordMeta(item.needPassword).tone}`">
                    <span class="status-dot" aria-hidden="true" />
                    {{ resolveNeedPasswordMeta(item.needPassword).label }}
                  </span>
                </td>
                <td>
                  <span class="status-chip" :class="`status-chip-${resolveUserStatusMeta(item.isActive).tone}`">
                    <span class="status-dot" aria-hidden="true" />
                    {{ resolveUserStatusMeta(item.isActive).label }}
                  </span>
                </td>
                <td>
                  <span class="text-ellipsis cell-ellipsis" :title="formatRange(item.validFrom, item.validUntil)">
                    {{ formatRange(item.validFrom, item.validUntil) }}
                  </span>
                </td>
                <td class="cell-number">{{ item.trialCount ?? '-' }}</td>
                <td class="cell-date">{{ formatDateTime(item.createTime) }}</td>
                <td class="cell-date">{{ formatDateTime(item.updateTime) }}</td>
                <td>
                  <button
                    class="btn btn-danger"
                    :disabled="item.isActive !== 1 || isDisableOperating || disablingUserIds.includes(item.id)"
                    @click="onDisableUser(item.id)"
                  >
                    {{
                      item.isActive !== 1
                        ? '已禁用'
                        : disablingUserIds.includes(item.id)
                          ? '禁用中...'
                          : '禁用'
                    }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button
            class="btn btn-light"
            :disabled="loading || isDisableOperating || current <= 1"
            @click="changePage(current - 1)"
          >
            上一页
          </button>
          <span class="pagination-summary">第 {{ current }} / {{ totalPages }} 页，共 {{ total }} 条</span>
          <button
            class="btn btn-light"
            :disabled="loading || isDisableOperating || current >= totalPages"
            @click="changePage(current + 1)"
          >
            下一页
          </button>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { adminUserApi, type AdminUserItem } from '@/api/adminUser'

const current = ref(1)
const size = ref(10)
const total = ref(0)
const totalPages = ref(1)
const users = ref<AdminUserItem[]>([])
const loading = ref(false)
const noticeMessage = ref('')
const loadErrorMessage = ref('')
const disableErrorMessage = ref('')
const disablingUserIds = ref<number[]>([])
const isDisableOperating = computed(() => disablingUserIds.value.length > 0)
const PAGE_DEFAULT_CURRENT = 1
const PAGE_DEFAULT_SIZE = 10
const NOTICE_EXPIRE_MS = 3000
let noticeTimer: ReturnType<typeof setTimeout> | null = null

// 统一错误文案
const getErrorMessage = (error: unknown) => {
  return error instanceof Error ? error.message : '操作失败'
}

// 统一正整数参数兜底
const sanitizePositiveInt = (value: number | null | undefined, fallback: number) => {
  const normalized = Number(value)
  if (!Number.isFinite(normalized)) {
    return fallback
  }
  return Math.max(1, Math.floor(normalized))
}

// 统一总条数兜底
const sanitizeTotal = (value: number | null | undefined) => {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized < 0) {
    return 0
  }
  return Math.floor(normalized)
}

// 统一页码范围限制
const clampPage = (page: number, maxPage: number) => {
  return Math.min(Math.max(1, page), Math.max(1, maxPage))
}

// 清理成功提示定时器
const clearNoticeTimer = () => {
  if (noticeTimer !== null) {
    clearTimeout(noticeTimer)
    noticeTimer = null
  }
}

// 清理成功提示文案
const clearNoticeMessage = () => {
  clearNoticeTimer()
  noticeMessage.value = ''
}

// 显示成功提示并自动过期
const showNoticeMessage = (message: string) => {
  clearNoticeTimer()
  noticeMessage.value = message
  noticeTimer = setTimeout(() => {
    noticeMessage.value = ''
    noticeTimer = null
  }, NOTICE_EXPIRE_MS)
}

// 统一日期展示格式
const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ')
}

// 统一有效期展示
const formatRange = (from: string | null, until: string | null) => {
  if (!from && !until) {
    return '-'
  }
  return `${formatDateTime(from)} ~ ${formatDateTime(until)}`
}

// 用户类型状态元信息
const resolveUserTypeMeta = (userType: number | null | undefined) => {
  if (userType === 0) {
    return { label: '管理员', tone: 'neutral' as const }
  }
  if (userType === 1) {
    return { label: '临时用户', tone: 'warning' as const }
  }
  return { label: '-', tone: 'neutral' as const }
}

// 密码要求状态元信息
const resolveNeedPasswordMeta = (needPassword: number | null | undefined) => {
  if (needPassword === 1) {
    return { label: '需要密码', tone: 'success' as const }
  }
  if (needPassword === 0) {
    return { label: '免密码', tone: 'neutral' as const }
  }
  return { label: '-', tone: 'neutral' as const }
}

// 账户状态元信息
const resolveUserStatusMeta = (isActive: number | null | undefined) => {
  if (isActive === 1) {
    return { label: '启用', tone: 'success' as const }
  }
  if (isActive === 0) {
    return { label: '禁用', tone: 'danger' as const }
  }
  return { label: '-', tone: 'neutral' as const }
}

interface LoadUsersOptions {
  page?: number
  force?: boolean
}

interface NormalizedUserPage {
  records: AdminUserItem[]
  current: number
  size: number
  total: number
  totalPages: number
}

// 请求并规范化分页响应
const requestUserPage = async (requestCurrent: number, requestSize: number): Promise<NormalizedUserPage> => {
  const page = await adminUserApi.pageUsers({
    current: requestCurrent,
    size: requestSize,
  })
  const safeSize = sanitizePositiveInt(page.size, requestSize)
  const safeTotal = sanitizeTotal(page.total)
  const safeTotalPages = Math.max(1, Math.ceil(safeTotal / safeSize))
  const safeCurrent = clampPage(sanitizePositiveInt(page.current, requestCurrent), safeTotalPages)
  return {
    records: Array.isArray(page.records) ? page.records : [],
    current: safeCurrent,
    size: safeSize,
    total: safeTotal,
    totalPages: safeTotalPages,
  }
}

// 加载用户分页数据
const loadUsers = async (options: LoadUsersOptions = {}) => {
  if (loading.value) {
    return
  }
  if (isDisableOperating.value && !options.force) {
    return
  }
  loading.value = true
  loadErrorMessage.value = ''
  try {
    let requestCurrent = sanitizePositiveInt(options.page ?? current.value, PAGE_DEFAULT_CURRENT)
    let requestSize = sanitizePositiveInt(size.value, PAGE_DEFAULT_SIZE)
    let normalizedPage = await requestUserPage(requestCurrent, requestSize)

    if (requestCurrent > normalizedPage.totalPages) {
      requestCurrent = normalizedPage.totalPages
      requestSize = normalizedPage.size
      normalizedPage = await requestUserPage(requestCurrent, requestSize)
    }

    users.value = normalizedPage.records
    total.value = normalizedPage.total
    size.value = normalizedPage.size
    current.value = normalizedPage.current
    totalPages.value = normalizedPage.totalPages
  } catch (error) {
    loadErrorMessage.value = getErrorMessage(error)
    users.value = []
    total.value = 0
    current.value = PAGE_DEFAULT_CURRENT
    totalPages.value = 1
  } finally {
    loading.value = false
  }
}

// 切换分页
const changePage = async (page: number) => {
  if (loading.value || isDisableOperating.value) {
    return
  }
  const safePage = clampPage(sanitizePositiveInt(page, current.value), totalPages.value)
  if (safePage === current.value) {
    return
  }
  await loadUsers({ page: safePage })
}

// 禁用用户
const onDisableUser = async (userId: number) => {
  if (loading.value || isDisableOperating.value || disablingUserIds.value.includes(userId)) {
    return
  }
  const confirmed = window.confirm(`确认禁用用户 ${userId} 吗？`)
  if (!confirmed) {
    return
  }

  clearNoticeMessage()
  disableErrorMessage.value = ''
  disablingUserIds.value = [...disablingUserIds.value, userId]
  try {
    await adminUserApi.disableUser(userId)
    showNoticeMessage(`用户 ${userId} 已禁用`)
    await loadUsers({ force: true })
  } catch (error) {
    disableErrorMessage.value = getErrorMessage(error)
  } finally {
    disablingUserIds.value = disablingUserIds.value.filter((id) => id !== userId)
  }
}

onMounted(async () => {
  await loadUsers()
})

onUnmounted(() => {
  clearNoticeTimer()
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

.feedback-banner {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  border: 1px solid transparent;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}

.feedback-success {
  color: #166534;
  border-color: #bbf7d0;
  background: #ecfdf3;
}

.feedback-error {
  color: #b91c1c;
  border-color: #fecaca;
  background: #fef2f2;
}

.table-scroll {
  max-width: 100%;
  max-height: 520px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
}

.table-grid {
  min-width: 1040px;
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
  white-space: nowrap;
}

.table-grid td {
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
}

.cell-date {
  text-align: center !important;
  font-variant-numeric: tabular-nums;
}

.cell-ellipsis {
  display: inline-block;
  width: 100%;
  max-width: 220px;
  vertical-align: middle;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.status-chip-neutral {
  color: #475569;
  border-color: #cbd5e1;
  background: #f1f5f9;
}

.status-chip-success {
  color: #166534;
  border-color: #bbf7d0;
  background: #ecfdf3;
}

.status-chip-warning {
  color: #9a3412;
  border-color: #fed7aa;
  background: #fff7ed;
}

.status-chip-danger {
  color: #b91c1c;
  border-color: #fecaca;
  background: #fef2f2;
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

.state-error {
  background: #fef2f2;
  border-color: #fecaca;
}

.state-loading,
.state-empty {
  background: var(--bg-soft);
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

.btn:disabled,
.btn-danger:disabled {
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

@media (max-width: 900px) {
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

  .table-grid {
    min-width: 920px;
  }

  .cell-ellipsis {
    max-width: 160px;
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
