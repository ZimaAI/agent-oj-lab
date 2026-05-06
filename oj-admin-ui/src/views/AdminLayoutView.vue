<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="brand">
        <p class="brand-title">AgentOJ 管理台</p>
        <p class="brand-subtitle">统一后台控制台</p>
      </div>

      <nav class="menu">
        <router-link
          v-for="item in menuItems"
          :key="item.to"
          :to="item.to"
          class="menu-item"
          :class="{ active: isActive(item.to) }"
          :aria-current="isActive(item.to) ? 'page' : undefined"
          :data-tooltip="item.label"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <button class="logout" @click="onLogout">退出登录</button>
    </aside>

    <main class="admin-content">
      <section class="context-bar" aria-label="当前模块">
        <p class="context-label">当前模块</p>
        <p class="context-value">{{ currentMenuLabel }}</p>
      </section>
      <section class="content-body scroll-container">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 侧边导航菜单
const menuItems = [
  { to: '/questions', label: '算法题管理' },
  { to: '/knowledge-segments', label: '知识片段/HitK任务' },
  { to: '/users', label: '用户日志' },
  { to: '/user-management', label: '用户管理' },
]

// 当前路由高亮判定
const isActive = (path: string) => route.path === path || route.path.startsWith(`${path}/`)

// 当前模块文案
const currentMenuLabel = computed(() => menuItems.find((item) => isActive(item.to))?.label ?? '管理控制台')

// 执行退出并返回登录页
const onLogout = async () => {
  authStore.logout()
  await router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  min-height: 100svh;
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
}

.admin-sidebar {
  position: sticky;
  top: 10px;
  align-self: start;
  height: calc(100vh - 20px);
  height: calc(100svh - 20px);
  height: calc(100dvh - 20px);
  padding: 16px 13px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background:
    linear-gradient(170deg, rgba(255, 255, 255, 0.96) 0%, rgba(238, 245, 255, 0.94) 100%);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(6px);
}

.brand {
  padding: 12px;
  border-radius: var(--radius-md);
  border: 1px solid #cfdcf2;
  background:
    radial-gradient(circle at top right, rgba(20, 87, 216, 0.13), transparent 56%),
    linear-gradient(180deg, #ffffff 0%, #f2f7ff 100%);
}

.brand-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  font-family: var(--font-family-heading);
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.brand-subtitle {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 44px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  color: var(--text-regular);
  padding: 10px 12px;
  background: transparent;
  font-weight: 600;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast),
    transform var(--transition-fast);
}

.menu-item:hover {
  background: #f6f9ff;
  border-color: #d0dcf0;
  transform: translateX(2px);
}

.menu-item:active {
  transform: translateX(1px);
}

.menu-item.active {
  border-color: #9eb8e6;
  background: linear-gradient(160deg, #edf4ff 0%, #e6f0ff 100%);
  color: #0f46aa;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.55);
}

.menu-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 999px;
  background: var(--brand);
}

.logout {
  margin-top: auto;
  width: 100%;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-sm);
  min-height: 42px;
  padding: 8px 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f2f7ff 100%);
  color: var(--text-regular);
  font-weight: 600;
  font-family: var(--font-family-heading);
  transition: background-color var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast);
}

.logout:hover {
  border-color: var(--border-strong);
  background: linear-gradient(180deg, #ffffff 0%, #eaf2ff 100%);
}

.logout:active {
  transform: translateY(1px);
}

.admin-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-lg);
}

.context-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid #d5e0f1;
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, #ffffff 0%, #f6faff 100%);
}

.context-label {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.context-value {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #133f8f;
}

.content-body {
  min-width: 0;
  padding: 2px 14px calc(14px + env(safe-area-inset-bottom, 0px));
}

@media (max-width: 1100px) {
  .admin-layout {
    grid-template-columns: 226px minmax(0, 1fr);
    gap: 9px;
    padding: 9px;
  }
}

@media (max-width: 900px) {
  .admin-layout {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
    gap: 8px;
  }

  .admin-sidebar {
    position: sticky;
    top: 8px;
    height: auto;
    padding: 12px;
  }

  .menu {
    flex-direction: row;
    flex-wrap: nowrap;
    overflow: auto;
    padding-bottom: 4px;
    scrollbar-width: thin;
  }

  .menu-item {
    flex: 0 0 auto;
    min-width: 110px;
    justify-content: center;
    transform: none;
  }

  .menu-item:hover,
  .menu-item:active {
    transform: none;
  }

  .menu-item.active::before {
    top: auto;
    bottom: 0;
    left: 10px;
    right: 10px;
    width: auto;
    height: 3px;
  }

  .logout {
    margin-top: 0;
  }

  .content-body {
    padding: 0 8px calc(12px + env(safe-area-inset-bottom, 0px));
  }

  .context-bar {
    min-height: 40px;
    padding: 0 10px;
  }
}
</style>
