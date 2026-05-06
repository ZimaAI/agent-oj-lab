<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()
</script>

<template>
  <div class="toast-container" aria-live="polite" aria-atomic="false">
    <transition-group name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :class="`toast-${toast.type}`"
        role="status"
      >
        <span class="toast-icon" aria-hidden="true">
          <svg v-if="toast.type === 'success'" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M5 12.5L9.2 16.5L19 7.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <svg v-else-if="toast.type === 'error'" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            <path d="M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2" />
            <path d="M12 10V16" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            <circle cx="12" cy="7.5" r="1" fill="currentColor" />
          </svg>
        </span>
        <span class="toast-message">{{ toast.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 1.15rem;
  right: 1rem;
  z-index: 1300;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  pointer-events: none;
}

.toast {
  display: inline-flex;
  align-items: flex-start;
  gap: 0.65rem;
  width: min(420px, calc(100vw - 2rem));
  padding: 0.72rem 0.88rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-background-soft);
  box-shadow: var(--shadow-sm);
  color: var(--color-text);
  pointer-events: auto;
}

.toast-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.25rem;
  height: 1.25rem;
  margin-top: 0.08rem;
  flex-shrink: 0;
}

.toast-icon svg {
  width: 100%;
  height: 100%;
}

.toast-message {
  font-size: 0.84rem;
  line-height: 1.45;
  color: var(--color-text);
}

.toast-success {
  border-color: rgba(22, 163, 74, 0.45);
  background: linear-gradient(180deg, rgba(22, 163, 74, 0.08), transparent 60%), var(--color-background-soft);
}

.toast-success .toast-icon {
  color: var(--color-success);
}

.toast-error {
  border-color: rgba(220, 38, 38, 0.45);
  background: linear-gradient(180deg, rgba(220, 38, 38, 0.09), transparent 60%), var(--color-background-soft);
}

.toast-error .toast-icon {
  color: var(--color-danger);
}

.toast-info {
  border-color: rgba(11, 102, 208, 0.4);
  background: linear-gradient(180deg, rgba(11, 102, 208, 0.09), transparent 60%), var(--color-background-soft);
}

.toast-info .toast-icon {
  color: var(--color-primary);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(16px);
}

@media (max-width: 640px) {
  .toast-container {
    top: 0.95rem;
    right: 0.6rem;
    left: 0.6rem;
  }

  .toast {
    width: 100%;
  }
}
</style>
