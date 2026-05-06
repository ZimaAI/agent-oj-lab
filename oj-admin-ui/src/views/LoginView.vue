<template>
  <div class="login-page">
    <section class="login-card" aria-label="管理端登录">
      <header class="login-header">
        <p class="login-kicker">AgentOJ Admin</p>
        <h1 class="login-title">后台管理登录</h1>
        <p class="login-subtitle text-ellipsis-2">使用管理员账号登录，统一管理题目、用户与审核流程。</p>
        <p class="login-tips">仅限已授权的后台管理员访问</p>
      </header>

      <form class="login-form" @submit.prevent="onSubmit">
        <label class="field">
          <span class="field-label">用户名</span>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
            required
            :disabled="loading"
          >
        </label>

        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            required
            :disabled="loading"
          >
        </label>

        <button class="submit-btn" type="submit" :disabled="isSubmitDisabled">
          <span class="submit-content">
            <span v-if="loading" class="loading-dot" aria-hidden="true" />
            <span>{{ loading ? '正在登录，请稍候...' : '登录' }}</span>
          </span>
        </button>
      </form>

      <p v-if="loading" class="state login-state" aria-live="polite">
        正在校验账号信息并建立会话...
      </p>
      <p v-else-if="errorMessage" class="state state-error login-state" role="alert" aria-live="assertive">
        登录失败：{{ errorMessage }}
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 维护登录表单状态
const form = reactive({
  username: '',
  password: '',
})
const loading = ref(false)
const errorMessage = ref('')
const DEFAULT_REDIRECT = '/questions'

// 统一处理输入首尾空格
const normalizedUsername = computed(() => form.username.trim())
const normalizedPassword = computed(() => form.password.trim())

// 统一提交按钮禁用条件
const isSubmitDisabled = computed(() => {
  return loading.value || !normalizedUsername.value || !normalizedPassword.value
})

// 仅允许站内相对路径跳转
const resolveSafeRedirect = (redirect: unknown) => {
  if (typeof redirect !== 'string') {
    return DEFAULT_REDIRECT
  }
  const trimmedRedirect = redirect.trim()
  if (!trimmedRedirect.startsWith('/') || trimmedRedirect.startsWith('//') || trimmedRedirect.startsWith('/\\')) {
    return DEFAULT_REDIRECT
  }
  return trimmedRedirect
}

// 保持登录流程并补齐输入与跳转安全校验
const onSubmit = async () => {
  if (loading.value) {
    return
  }
  if (isSubmitDisabled.value) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  const username = normalizedUsername.value
  const password = normalizedPassword.value
  loading.value = true
  errorMessage.value = ''
  try {
    await authStore.login({ username, password })
    const redirect = resolveSafeRedirect(route.query.redirect)
    await router.push(redirect)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background:
    radial-gradient(circle at 14% 12%, rgba(20, 87, 216, 0.2), transparent 46%),
    radial-gradient(circle at 90% 80%, rgba(15, 139, 83, 0.12), transparent 42%),
    linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
}

.login-card {
  width: min(468px, 100%);
  padding: 30px 24px 22px;
  border: 1px solid #cdddfe;
  border-radius: var(--radius-lg);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(245, 250, 255, 0.98) 100%);
  box-shadow: 0 20px 42px rgba(14, 43, 88, 0.14);
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #1b64e8 0%, #0f8b53 100%);
}

.login-header {
  margin-bottom: 20px;
}

.login-kicker {
  margin: 0;
  color: var(--brand);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.login-title {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.25;
  font-family: var(--font-family-heading);
  color: var(--text-primary);
}

.login-subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.login-tips {
  margin: 10px 0 0;
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border: 1px solid #c7d6ef;
  border-radius: 999px;
  background: #f2f7ff;
  color: #305483;
  font-size: 12px;
  font-weight: 600;
}

.login-form {
  display: grid;
  gap: 12px;
}

.field {
  display: grid;
  gap: 7px;
}

.field-label {
  color: var(--text-regular);
  font-size: 13px;
  font-weight: 600;
}

.field input {
  height: 42px;
  letter-spacing: 0.01em;
}

.field input:disabled {
  background: var(--bg-muted);
  color: var(--text-secondary);
}

.submit-btn {
  margin-top: 4px;
  min-height: 46px;
  font-size: 14px;
}

.submit-content {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.loading-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}

.login-state {
  margin: 12px 0 0;
  min-height: 44px;
  font-size: 13px;
  word-break: break-word;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 680px) {
  .login-page {
    padding: 12px;
    align-items: stretch;
  }

  .login-card {
    margin: auto 0;
    padding: 24px 16px 18px;
  }

  .login-title {
    font-size: 22px;
  }
}
</style>
