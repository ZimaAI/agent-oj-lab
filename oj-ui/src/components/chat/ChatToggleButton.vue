<template>
  <Transition name="chat-toggle">
    <button
      v-if="!isOpen"
      class="chat-toggle-btn"
      type="button"
      aria-label="打开 AI 助手"
      @click="$emit('toggle')"
    >
      <div class="btn-content">
        <svg class="ai-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span class="btn-text">AI</span>
      </div>
      <div class="btn-glow"></div>
    </button>
  </Transition>
</template>

<script setup lang="ts">
interface Props {
  isOpen: boolean
}

defineProps<Props>()
defineEmits<{
  toggle: []
}>()
</script>

<style scoped>
.chat-toggle-btn {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 100;

  width: 56px;
  height: 56px;
  border: none;
  border-radius: 16px;

  border: 1px solid rgba(11, 102, 208, 0.5);
  background: linear-gradient(145deg, var(--color-primary), var(--color-primary-dark));
  box-shadow: 0 10px 24px rgba(11, 102, 208, 0.26);

  cursor: pointer;
  overflow: hidden;

  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.btn-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  height: 100%;
  color: white;
}

.ai-icon {
  width: 18px;
  height: 18px;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.btn-text {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.3px;
  line-height: 1;
  font-family: var(--font-ui);
}

.btn-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at center, rgba(255, 255, 255, 0.3) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.chat-toggle-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 26px rgba(11, 102, 208, 0.3);
}

.chat-toggle-btn:hover .ai-icon {
  transform: scale(1.1) rotate(5deg);
}

.chat-toggle-btn:hover .btn-glow {
  opacity: 1;
}

.chat-toggle-btn:active {
  transform: translateY(0) scale(0.98);
}

.chat-toggle-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 4px var(--primary-ring);
}

/* Transition animations */
.chat-toggle-enter-active {
  animation: slideInBounce 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.chat-toggle-leave-active {
  animation: slideOutFade 0.3s ease-out;
}

@keyframes slideInBounce {
  0% {
    opacity: 0;
    transform: translateX(100px) scale(0.8);
  }
  100% {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}

@keyframes slideOutFade {
  0% {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translateX(100px) scale(0.8);
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-toggle-enter-active,
  .chat-toggle-leave-active {
    animation: none;
  }

  .chat-toggle-btn,
  .ai-icon {
    transition: none;
  }
}
</style>
