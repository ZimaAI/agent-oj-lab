<template>
  <div v-if="show" class="modal-overlay" role="presentation" @click="handleOverlayClick">
    <div
      class="modal-content"
      role="dialog"
      aria-modal="true"
      aria-labelledby="auth-modal-title"
      @click.stop
    >
      <div class="modal-header">
        <div class="header-main">
          <span class="header-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.8" />
              <path d="M8 10V7.5C8 5.57 9.57 4 11.5 4H12.5C14.43 4 16 5.57 16 7.5V10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </span>
          <h3 id="auth-modal-title">需要登录</h3>
        </div>
        <button type="button" class="close-button" aria-label="关闭弹窗" @click="close">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M6 6L18 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="modal-body">
        <p>{{ message || '此操作需要登录，请先登录后再试。' }}</p>
      </div>

      <div class="modal-footer">
        <button type="button" class="cancel-button" @click="close">取消</button>
        <button type="button" class="login-button" @click="goToLogin">去登录</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

interface Props {
  show: boolean
  message?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  close: []
}>()

const router = useRouter()

function close() {
  emit('close')
}

function handleOverlayClick() {
  close()
}

function goToLogin() {
  close()
  router.push('/login')
}

// 弹窗打开时支持 Esc 快速关闭，减少键盘用户操作成本。
const handleEscapeKey = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && props.show) {
    close()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscapeKey)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscapeKey)
})
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(5, 16, 31, 0.48);
  backdrop-filter: blur(4px);
}

.modal-content {
  width: min(420px, calc(100vw - 2rem));
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-background-soft);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.95rem 1rem;
  border-bottom: 1px solid var(--color-border);
}

.header-main {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--color-primary);
  background: var(--color-primary-dim);
}

.header-icon svg {
  width: 15px;
  height: 15px;
}

.modal-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-text);
}

.close-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;
}

.close-button svg {
  width: 16px;
  height: 16px;
}

.close-button:hover {
  border-color: var(--color-border);
  color: var(--color-text);
  background: var(--color-background-mute);
}

.modal-body {
  padding: 1.1rem 1rem;
}

.modal-body p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.65;
  font-size: 0.9rem;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.65rem;
  padding: 0.95rem 1rem;
  border-top: 1px solid var(--color-border);
}

.cancel-button,
.login-button {
  min-height: 36px;
  min-width: 84px;
  padding: 0 0.9rem;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
}

.cancel-button {
  border: 1px solid var(--color-border);
  background: var(--color-background-mute);
  color: var(--color-text-secondary);
}

.cancel-button:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.login-button {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.login-button:hover {
  transform: translateY(-1px);
  border-color: var(--color-primary-dark);
  background: var(--color-primary-dark);
}

@media (max-width: 560px) {
  .modal-footer {
    flex-direction: column-reverse;
  }

  .cancel-button,
  .login-button {
    width: 100%;
  }
}
</style>
