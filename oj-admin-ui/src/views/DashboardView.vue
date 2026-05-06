<template>
  <main class="dashboard-page">
    <header class="dashboard-header card">
      <div class="header-main">
        <p class="header-kicker">Admin Console</p>
        <h1 class="header-title">运营总览入口</h1>
        <p class="header-subtitle text-ellipsis-2">集中管理算法题、用户日志与账号权限，统一交互反馈与状态展示。</p>
      </div>
      <button class="btn btn-light logout-btn" @click="onLogout">退出登录</button>
    </header>

    <section class="cards-grid" aria-label="后台管理入口">
      <router-link
        v-for="item in dashboardCards"
        :key="item.to"
        :to="item.to"
        class="card dashboard-card"
        :data-tooltip="item.title"
      >
        <div class="card-head">
          <p class="card-kicker">{{ item.kicker }}</p>
          <h2 class="card-title text-ellipsis">{{ item.title }}</h2>
        </div>
        <p class="card-description text-ellipsis-2">{{ item.description }}</p>
        <div class="card-foot">
          <span class="card-status">{{ item.status }}</span>
          <span class="card-arrow" aria-hidden="true">→</span>
        </div>
      </router-link>
    </section>

    <p class="state dashboard-state">
      提示：标题较长时会自动省略；悬停可查看完整信息；移动端会切换为单列布局。
    </p>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

// 仪表盘入口配置
const dashboardCards = [
  {
    to: '/questions',
    kicker: 'Question',
    title: '算法题管理',
    description: '维护题目内容、难度与标签，支持批量操作与文档联动。',
    status: '题库维护',
  },
  {
    to: '/users',
    kicker: 'Activity',
    title: '用户日志',
    description: '查看登录与行为日志，辅助审计追踪与异常定位。',
    status: '日志查询',
  },
  {
    to: '/user-management',
    kicker: 'Account',
    title: '用户管理',
    description: '统一管理后台账号状态、有效期与权限策略。',
    status: '账号维护',
  },
]

// 保持退出登录行为
const onLogout = async () => {
  authStore.logout()
  await router.push('/login')
}
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 14px;
  padding: 2px 0;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 20px;
  background:
    radial-gradient(circle at top right, rgba(20, 87, 216, 0.14), transparent 46%),
    linear-gradient(180deg, #ffffff 0%, #f5faff 100%);
}

.header-main {
  min-width: 0;
  display: grid;
  gap: 5px;
  max-width: 880px;
}

.header-kicker {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--brand);
}

.header-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.22;
  font-family: var(--font-family-heading);
  color: var(--text-primary);
}

.header-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.logout-btn {
  flex: 0 0 auto;
  min-width: 104px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}

.dashboard-card {
  display: grid;
  gap: 12px;
  min-height: 188px;
  padding: 16px;
  border-color: #cfdcf2;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
}

.dashboard-card:hover {
  border-color: #8fb0e2;
  box-shadow: 0 16px 28px rgba(20, 87, 216, 0.14);
  transform: translateY(-2px);
}

.dashboard-card:focus-visible {
  box-shadow: var(--shadow-focus), 0 16px 28px rgba(20, 87, 216, 0.14);
  border-color: #7fa4de;
}

.dashboard-card:active {
  transform: translateY(0);
}

.card-head {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.card-kicker {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.card-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
  line-height: 1.28;
  font-family: var(--font-family-heading);
}

.card-description {
  margin: 0;
  color: var(--text-regular);
  font-size: 13px;
  line-height: 1.62;
}

.card-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border: 1px solid #bed0eb;
  border-radius: 999px;
  background: #ecf3ff;
  color: #1147a8;
  font-size: 12px;
  font-weight: 700;
}

.card-arrow {
  color: var(--text-secondary);
  font-size: 17px;
  transition: transform var(--transition-fast), color var(--transition-fast);
}

.dashboard-card:hover .card-arrow {
  color: var(--brand);
  transform: translateX(2px);
}

.dashboard-state {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid #d4e0f2;
  background: #f7fbff;
  font-size: 13px;
  color: var(--text-secondary);
  word-break: break-word;
}

@media (max-width: 1100px) {
  .cards-grid {
    gap: 12px;
  }
}

@media (max-width: 780px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
    padding: 14px;
  }

  .header-title {
    font-size: 22px;
  }

  .logout-btn {
    width: 100%;
  }

  .cards-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-card {
    min-height: 168px;
    padding: 14px;
  }
}
</style>
