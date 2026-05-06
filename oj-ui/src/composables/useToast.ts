import { ref } from 'vue'

export interface Toast {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

const toasts = ref<Toast[]>([])
let nextId = 0

export function useToast() {
  const show = (message: string, type: Toast['type'] = 'info', duration = 2000) => {
    const id = nextId++
    const toast: Toast = { id, message, type }

    toasts.value.push(toast)

    setTimeout(() => {
      const index = toasts.value.findIndex(t => t.id === id)
      if (index !== -1) {
        toasts.value.splice(index, 1)
      }
    }, duration)
  }

  const success = (message: string, duration?: number) => {
    show(message, 'success', duration)
  }

  const error = (message: string, duration?: number) => {
    show(message, 'error', duration)
  }

  const info = (message: string, duration?: number) => {
    show(message, 'info', duration)
  }

  return {
    toasts,
    show,
    success,
    error,
    info,
  }
}
