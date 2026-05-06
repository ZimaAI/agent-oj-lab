<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ToastContainer from '@/components/ToastContainer.vue'

const router = useRouter()
const authStore = useAuthStore()
const showDropdown = ref(false)

const isAuthenticated = computed(() => authStore.isAuthenticated)
const trialCount = computed(() => authStore.trialCount)
const showTrialCount = computed(() => {
  return isAuthenticated.value && trialCount.value !== null && trialCount.value >= 0
})
const usernameInitial = computed(() => {
  const username = authStore.currentUser?.username || authStore.userInfo?.username || 'U'
  return username.charAt(0).toUpperCase()
})

const applyLightTheme = () => {
  document.documentElement.setAttribute('data-theme', 'light')
  localStorage.removeItem('oj-theme')
}

function handleLogout() {
  showDropdown.value = false
  authStore.logout()
  router.push('/login')
}

function handleAvatarClick() {
  if (!isAuthenticated.value) {
    router.push('/login')
    return
  }

  showDropdown.value = !showDropdown.value
}

function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (!target) {
    return
  }

  if (!target.closest('[data-user-menu-root]')) {
    showDropdown.value = false
  }
}

onMounted(() => {
  applyLightTheme()

  document.addEventListener('click', handleClickOutside)

  if (authStore.isAuthenticated) {
    void authStore.fetchCurrentUser()
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="app-shell">
    <header class="app-nav-wrap">
      <nav class="app-nav" aria-label="主导航">
        <div class="nav-left">
          <RouterLink to="/" class="nav-brand" aria-label="返回首页">
            <span class="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 3L20 7.5V16.5L12 21L4 16.5V7.5L12 3Z" stroke="currentColor" stroke-width="1.8" />
                <path d="M8.5 10.25H15.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                <path d="M8.5 13.75H12.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </span>
            <span class="brand-name">Agent OJ</span>
          </RouterLink>

          <div class="primary-nav" aria-label="核心导航">
            <RouterLink to="/" class="primary-nav-link" exact-active-class="is-active">首页</RouterLink>
            <RouterLink to="/problems" class="primary-nav-link" active-class="is-active">题库</RouterLink>
          </div>
        </div>

        <div class="nav-actions">
          <RouterLink to="/algorithm" class="nav-link" aria-label="历史记录">
            <svg class="nav-link-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 8V12L14.5 14.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M3 12C3 7.03 7.03 3 12 3C16.97 3 21 7.03 21 12C21 16.97 16.97 21 12 21C8.31 21 5.13 18.78 3.75 15.6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            <span class="nav-link-label">历史记录</span>
          </RouterLink>

          <RouterLink
            v-if="isAuthenticated"
            to="/spans"
            class="nav-link"
            aria-label="执行链路"
          >
            <svg class="nav-link-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M5 19V10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <path d="M12 19V5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <path d="M19 19V13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            <span class="nav-link-label">执行链路</span>
          </RouterLink>

          <span v-if="showTrialCount" class="trial-count" :class="{ exhausted: trialCount === 0 }" aria-live="polite">
            <span class="trial-dot" aria-hidden="true"></span>
            剩余 {{ trialCount }} 次对话
          </span>

          <div class="user-menu" data-user-menu-root>
            <button
              class="avatar-button"
              type="button"
              :class="{ authenticated: isAuthenticated }"
              :aria-expanded="showDropdown"
              :aria-haspopup="isAuthenticated"
              :aria-label="isAuthenticated ? '打开用户菜单' : '前往登录'"
              @click="handleAvatarClick"
            >
              <span>{{ usernameInitial }}</span>
            </button>

            <div v-if="showDropdown && isAuthenticated" class="user-dropdown" role="menu">
              <button class="dropdown-item" type="button" role="menuitem" @click="handleLogout">
                <svg class="dropdown-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M15 17L20 12L15 7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                  <path d="M20 12H9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  <path d="M9 4H5C4.45 4 4 4.45 4 5V19C4 19.55 4.45 20 5 20H9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                </svg>
                退出登录
              </button>
            </div>
          </div>
        </div>
      </nav>
    </header>

    <main class="app-main">
      <RouterView />
    </main>

    <ToastContainer />
  </div>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&family=Sora:wght@500;600;700&display=swap');

:root {
  color-scheme: light;
  --bg: #ffffff;
  --surface: #ffffff;
  --card: #f8fafc;
  --surface-elevated: rgba(255, 255, 255, 0.9);
  --surface-overlay: rgba(255, 255, 255, 0.95);
  --border: #d4e1f2;
  --primary: #0b66d0;
  --primary-dark: #0853aa;
  --primary-dim: rgba(11, 102, 208, 0.12);
  --primary-ring: rgba(11, 102, 208, 0.34);
  --success: #16a34a;
  --success-light: rgba(22, 163, 74, 0.16);
  --success-dark: #15803d;
  --warning: #d97706;
  --warning-dim: rgba(217, 119, 6, 0.12);
  --error: #dc2626;
  --error-dim: rgba(220, 38, 38, 0.12);
  --text: #0c2142;
  --text-muted: #4a658d;
  --text-dim: #6b85ad;
  --font-ui: 'IBM Plex Sans', sans-serif;
  --font-heading: 'Sora', sans-serif;
  --font-mono: 'JetBrains Mono', monospace;
  --radius-sm: 10px;
  --radius-md: 14px;
  --radius-lg: 18px;
  --shadow-sm: 0 10px 24px rgba(13, 31, 66, 0.08);
  --shadow-md: 0 20px 42px rgba(13, 31, 66, 0.14);
  --transition-theme: background-color 0.25s ease, color 0.25s ease, border-color 0.25s ease;
  --nav-height: 64px;
  --container-max: 1340px;

  --color-background: var(--bg);
  --color-background-soft: var(--surface);
  --color-background-mute: var(--card);
  --color-border: var(--border);
  --color-border-hover: var(--primary);
  --color-text: var(--text);
  --color-text-secondary: var(--text-muted);
  --color-primary: var(--primary);
  --color-primary-dim: var(--primary-dim);
  --color-primary-dark: var(--primary-dark);
  --color-primary-hover: var(--primary-dark);
  --color-success: var(--success);
  --color-success-light: var(--success-light);
  --color-success-dark: var(--success-dark);
  --color-danger: var(--error);
  --color-danger-light: var(--error-dim);
  --color-error: var(--error);
  --color-warning: var(--warning);
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body,
#app {
  width: 100%;
  height: 100%;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font-family: var(--font-ui);
  line-height: 1.5;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  transition: var(--transition-theme);
}

a {
  color: inherit;
  text-decoration: none;
}

button,
input,
select,
textarea {
  font: inherit;
  color: inherit;
}

:focus-visible {
  outline: 2px solid var(--primary-ring);
  outline-offset: 2px;
}

::selection {
  background: var(--primary-dim);
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
</style>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  width: 100%;
  overflow: hidden;
}

.app-nav-wrap {
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  padding: 0;
  background: var(--color-background);
}

.app-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  width: 100%;
  min-height: var(--nav-height);
  margin: 0;
  padding: 0.55rem clamp(0.8rem, 2vw, 2.4rem);
  border-radius: 0;
  border: none;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-background);
  backdrop-filter: none;
  box-shadow: none;
}

.nav-left {
  display: inline-flex;
  align-items: center;
  gap: 0.85rem;
  min-width: 0;
}

.nav-brand {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 40px;
  padding: 0 0.15rem;
  border-radius: var(--radius-sm);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--color-primary);
  background: var(--color-primary-dim);
}

.brand-mark svg {
  width: 20px;
  height: 20px;
}

.brand-name {
  font-family: var(--font-heading);
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--color-text);
}

.primary-nav {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}

.primary-nav-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 0.95rem;
  border-radius: 12px;
  color: var(--color-text-secondary);
  font-size: 1.02rem;
  font-weight: 600;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.primary-nav-link:hover {
  color: var(--color-primary);
}

.primary-nav-link.is-active {
  background: var(--color-primary-dim);
  color: var(--color-primary);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  min-height: 40px;
  padding: 0 0.85rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-background-soft);
  color: var(--color-text-secondary);
  font-size: 0.84rem;
  font-weight: 600;
  transition: transform 0.18s ease, border-color 0.18s ease, color 0.18s ease, background-color 0.18s ease;
}

.nav-link-icon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-primary-dim);
  transform: translateY(-1px);
}

.trial-count {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 40px;
  padding: 0 0.78rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--warning);
  background: var(--warning-dim);
  color: var(--warning);
  font-size: 0.82rem;
  font-weight: 600;
}

.trial-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.trial-count.exhausted {
  border-color: var(--color-danger);
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.user-menu {
  position: relative;
}

.avatar-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  background: var(--color-background-soft);
  color: var(--color-text-secondary);
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, color 0.18s ease, background-color 0.18s ease;
}

.avatar-button.authenticated {
  border-color: rgba(22, 163, 74, 0.5);
  color: var(--color-success);
}

.avatar-button:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-primary-dim);
  transform: translateY(-1px);
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 170px;
  padding: 0.4rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-md);
}

.dropdown-item {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  width: 100%;
  min-height: 36px;
  padding: 0 0.65rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-danger);
  font-size: 0.83rem;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.dropdown-item:hover {
  border-color: rgba(220, 38, 38, 0.35);
  background: var(--color-danger-light);
}

.dropdown-icon {
  width: 15px;
  height: 15px;
}

.app-main {
  flex: 1;
  width: 100%;
  min-height: 0;
  overflow: auto;
}

@media (max-width: 980px) {
  .brand-name {
    font-size: 0.94rem;
  }

  .primary-nav-link {
    min-height: 40px;
    padding: 0 0.8rem;
    font-size: 0.94rem;
  }

  .nav-link {
    padding: 0 0.68rem;
  }
}

@media (max-width: 760px) {
  .trial-count {
    display: none;
  }

  .nav-link {
    min-height: 38px;
    padding: 0 0.58rem;
  }

  .nav-link-label {
    font-size: 0.78rem;
  }

  .primary-nav-link {
    min-height: 38px;
    padding: 0 0.72rem;
  }
}

@media (max-width: 560px) {
  .app-nav {
    gap: 0.45rem;
    padding: 0.5rem;
  }

  .brand-name {
    display: none;
  }

  .brand-mark {
    width: 34px;
    height: 34px;
  }

  .primary-nav {
    gap: 0.12rem;
  }

  .primary-nav-link {
    min-height: 36px;
    padding: 0 0.65rem;
    font-size: 0.85rem;
  }

  .nav-link-label {
    display: none;
  }

  .nav-link {
    width: 38px;
    justify-content: center;
    padding: 0;
  }
}
</style>
