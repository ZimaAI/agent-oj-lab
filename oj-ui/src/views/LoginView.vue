<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  buildExperienceLoginRequest,
  getExperienceAccountError,
  resolvePostLoginRedirect,
} from './loginForm'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const experienceAccount = ref('test')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  const accountError = getExperienceAccountError(experienceAccount.value)
  if (accountError) {
    error.value = accountError
    return
  }

  loading.value = true
  error.value = ''

  try {
    await authStore.login(buildExperienceLoginRequest(experienceAccount.value))

    const returnUrl = localStorage.getItem('return_url')
    const redirectTarget = resolvePostLoginRedirect(route.query.redirect, returnUrl)
    localStorage.removeItem('return_url')
    router.push(redirectTarget)
  } catch (e: any) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-layout">
      <section class="login-side" aria-label="平台介绍">
        <span class="side-kicker">Agent OJ</span>
        <h1 class="side-title">智能算法训练平台</h1>
        <p class="side-description">基于 Agent 的出题、评测与反馈闭环，帮助你持续提升算法能力。</p>
        <ul class="side-list">
          <li>按难度动态出题，持续贴合当前能力</li>
          <li>支持代码运行与评测结果可视化</li>
          <li>链路追踪可回放，便于定位问题</li>
        </ul>
      </section>

      <section class="login-panel" aria-label="登录表单区域">
        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-header">
            <h2 class="form-title">登录</h2>
            <p class="form-subtitle">输入体验账号继续使用 Agent OJ</p>
          </div>

          <div class="form-body">
            <label class="field-label" for="experience-account">体验账号</label>
            <input
              id="experience-account"
              v-model="experienceAccount"
              type="text"
              class="field-input"
              placeholder="请输入体验账号"
              autocomplete="username"
              :aria-invalid="Boolean(error)"
              :aria-describedby="error ? 'login-error' : undefined"
              :disabled="loading"
            />

            <div v-if="error" id="login-error" class="form-error" role="alert">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" />
                <path d="M12 8V13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                <circle cx="12" cy="16" r="1" fill="currentColor" />
              </svg>
              <span>{{ error }}</span>
            </div>

            <button type="submit" class="submit-button" :disabled="loading" :aria-busy="loading">
              <span v-if="loading" class="button-loader" aria-hidden="true"></span>
              <span>{{ loading ? '登录中...' : '登录' }}</span>
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100dvh - 88px);
  padding: 2rem 1rem 2.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-layout {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  overflow: hidden;
  background: var(--color-background-soft);
  box-shadow: var(--shadow-md);
}

.login-side {
  padding: clamp(1.4rem, 3.2vw, 2.6rem);
  background:
    radial-gradient(520px 260px at 18% 6%, rgba(14, 165, 233, 0.25), transparent 60%),
    linear-gradient(150deg, rgba(11, 102, 208, 0.9), rgba(13, 83, 160, 0.88));
  color: #e6f1ff;
}

.side-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 0.72rem;
  border: 1px solid rgba(230, 241, 255, 0.35);
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.side-title {
  margin: 1rem 0 0;
  font-family: var(--font-heading);
  font-size: clamp(1.55rem, 2.6vw, 2.2rem);
  line-height: 1.25;
}

.side-description {
  margin: 0.9rem 0 0;
  color: rgba(230, 241, 255, 0.92);
  line-height: 1.72;
  font-size: 0.93rem;
}

.side-list {
  margin: 1.2rem 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.72rem;
}

.side-list li {
  position: relative;
  padding-left: 1rem;
  color: rgba(230, 241, 255, 0.92);
  font-size: 0.88rem;
  line-height: 1.6;
}

.side-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.55em;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
}

.login-panel {
  padding: clamp(1.2rem, 3vw, 2.4rem);
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent), var(--color-background-soft);
}

.login-form {
  width: min(420px, 100%);
}

.form-header {
  margin-bottom: 1.3rem;
}

.form-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-text);
}

.form-subtitle {
  margin: 0.45rem 0 0;
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.form-body {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.field-label {
  color: var(--color-text);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.field-input {
  width: 100%;
  min-height: 42px;
  padding: 0 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-background-mute);
  color: var(--color-text);
  font-size: 0.92rem;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.field-input::placeholder {
  color: var(--color-text-secondary);
}

.field-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--primary-ring);
}

.field-input:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.form-error {
  display: inline-flex;
  align-items: center;
  gap: 0.48rem;
  padding: 0.6rem 0.72rem;
  border: 1px solid rgba(220, 38, 38, 0.45);
  border-radius: var(--radius-sm);
  background: var(--color-danger-light);
  color: var(--color-danger);
  font-size: 0.83rem;
}

.form-error svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.submit-button {
  margin-top: 0.35rem;
  min-height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: 0.92rem;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, background-color 0.18s ease, border-color 0.18s ease;
}

.submit-button:hover:not(:disabled) {
  transform: translateY(-1px);
  background: var(--color-primary-dark);
  border-color: var(--color-primary-dark);
}

.submit-button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.button-loader {
  width: 0.95rem;
  height: 0.95rem;
  border: 2px solid rgba(255, 255, 255, 0.38);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.65s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .login-layout {
    grid-template-columns: 1fr;
  }

  .login-side {
    padding-bottom: 1.4rem;
  }
}
</style>
