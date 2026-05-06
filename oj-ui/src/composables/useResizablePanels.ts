import { ref, onMounted, onUnmounted } from 'vue'

interface PanelWidths {
  sidebar: number
  problem: number
  chat: number
  editor: number
}

const MIN_WIDTH = 300
const DEFAULT_SIDEBAR_WIDTH = 270
const MIN_SIDEBAR_WIDTH = 220
const MAX_SIDEBAR_WIDTH = 300
const LEGACY_FORCED_SIDEBAR_WIDTH_THRESHOLD = 300
const DEFAULT_PROBLEM_WIDTH = 400
const DEFAULT_CHAT_WIDTH = 400
const DEBOUNCE_DELAY = 16 // ~60fps
const PANEL_WIDTHS_STORAGE_KEY = 'panel-widths'

// Debounce utility
function debounce<T extends (...args: any[]) => void>(fn: T, delay: number): T {
  let timeoutId: ReturnType<typeof setTimeout> | null = null
  return ((...args: any[]) => {
    if (timeoutId) clearTimeout(timeoutId)
    timeoutId = setTimeout(() => fn(...args), delay)
  }) as T
}

function toFiniteNumber(value: unknown, fallback: number): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

export function useResizablePanels() {
  const sidebarWidth = ref(DEFAULT_SIDEBAR_WIDTH)
  const problemWidth = ref(DEFAULT_PROBLEM_WIDTH)
  const chatWidth = ref(DEFAULT_CHAT_WIDTH)
  const editorWidth = ref(0)

  const sidebarCollapsed = ref(false)
  const chatCollapsed = ref(false)

  const isDragging = ref(false)
  const dragTarget = ref<'problem' | 'chat' | null>(null)
  let rafId: number | null = null

  // Save widths to localStorage
  const saveWidths = () => {
    const widths: PanelWidths = {
      sidebar: sidebarWidth.value,
      problem: problemWidth.value,
      chat: chatWidth.value,
      editor: editorWidth.value
    }
    localStorage.setItem(PANEL_WIDTHS_STORAGE_KEY, JSON.stringify(widths))
  }

  // Load saved widths from localStorage
  const loadWidths = () => {
    const saved = localStorage.getItem(PANEL_WIDTHS_STORAGE_KEY)
    if (saved) {
      try {
        const widths = JSON.parse(saved) as Partial<PanelWidths>
        const parsedSidebarWidth = toFiniteNumber(widths.sidebar, DEFAULT_SIDEBAR_WIDTH)
        const migratedSidebarWidth =
          parsedSidebarWidth >= LEGACY_FORCED_SIDEBAR_WIDTH_THRESHOLD
            ? DEFAULT_SIDEBAR_WIDTH
            : parsedSidebarWidth
        sidebarWidth.value = clamp(migratedSidebarWidth, MIN_SIDEBAR_WIDTH, MAX_SIDEBAR_WIDTH)
        problemWidth.value = Math.max(toFiniteNumber(widths.problem, DEFAULT_PROBLEM_WIDTH), MIN_WIDTH)
        chatWidth.value = Math.max(toFiniteNumber(widths.chat, DEFAULT_CHAT_WIDTH), 0)
        editorWidth.value = Math.max(toFiniteNumber(widths.editor, 0), 0)
        saveWidths()
      } catch (e) {
        console.error('Failed to parse saved panel widths:', e)
      }
    }
  }

  // Debounced save function
  const debouncedSave = debounce(saveWidths, 300)

  // Toggle sidebar collapse
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
    saveWidths()
  }

  // Toggle chat collapse
  const toggleChat = () => {
    chatCollapsed.value = !chatCollapsed.value
    saveWidths()
  }

  // Start dragging
  const startDrag = (target: 'problem' | 'chat', event: MouseEvent) => {
    isDragging.value = true
    dragTarget.value = target
    event.preventDefault()
  }

  // 键盘微调面板宽度，保证键盘用户可完成与鼠标一致的布局调整。
  const resizeByKeyboard = (target: 'problem' | 'chat', delta: number) => {
    const sidebarOffset = sidebarCollapsed.value ? 0 : sidebarWidth.value
    const containerWidth = window.innerWidth - sidebarOffset
    const minEditorWidth = MIN_WIDTH

    if (target === 'problem') {
      const maxProblemWidth = Math.max(
        MIN_WIDTH,
        containerWidth - (chatCollapsed.value ? 0 : chatWidth.value) - minEditorWidth,
      )
      problemWidth.value = clamp(problemWidth.value + delta, MIN_WIDTH, maxProblemWidth)
      debouncedSave()
      onWindowResize()
      return
    }

    if (chatCollapsed.value) {
      return
    }

    const maxChatWidth = Math.max(0, containerWidth - problemWidth.value - minEditorWidth)
    chatWidth.value = clamp(chatWidth.value + delta, 0, maxChatWidth)
    debouncedSave()
    onWindowResize()
  }

  // Handle drag move with RAF for smooth performance
  const onDragMove = (event: MouseEvent) => {
    if (!isDragging.value || !dragTarget.value) return

    if (rafId) cancelAnimationFrame(rafId)

    rafId = requestAnimationFrame(() => {
      const containerWidth = window.innerWidth - (sidebarCollapsed.value ? 0 : sidebarWidth.value)

      if (dragTarget.value === 'problem') {
        const newProblemWidth = event.clientX - (sidebarCollapsed.value ? 0 : sidebarWidth.value)
        if (newProblemWidth >= MIN_WIDTH) {
          problemWidth.value = newProblemWidth
        }
      } else if (dragTarget.value === 'chat') {
        const chatStart = (sidebarCollapsed.value ? 0 : sidebarWidth.value) + problemWidth.value
        const newChatWidth = event.clientX - chatStart
        if (newChatWidth >= 0) {
          chatWidth.value = newChatWidth
        }
      }

      debouncedSave()
    })
  }

  // Stop dragging
  const stopDrag = () => {
    if (isDragging.value) {
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      isDragging.value = false
      dragTarget.value = null
      saveWidths() // Save immediately when drag ends
    }
  }

  // Handle window resize with debouncing
  const onWindowResize = () => {
    const totalWidth = window.innerWidth
    const usedWidth = (sidebarCollapsed.value ? 0 : sidebarWidth.value) +
                      problemWidth.value +
                      (chatCollapsed.value ? 0 : chatWidth.value)
    editorWidth.value = Math.max(MIN_WIDTH, totalWidth - usedWidth)
  }

  const debouncedWindowResize = debounce(onWindowResize, 150)

  onMounted(() => {
    loadWidths()
    onWindowResize()
    window.addEventListener('mousemove', onDragMove)
    window.addEventListener('mouseup', stopDrag)
    window.addEventListener('resize', debouncedWindowResize)
  })

  onUnmounted(() => {
    if (rafId) cancelAnimationFrame(rafId)
    window.removeEventListener('mousemove', onDragMove)
    window.removeEventListener('mouseup', stopDrag)
    window.removeEventListener('resize', debouncedWindowResize)
  })

  return {
    sidebarWidth,
    problemWidth,
    chatWidth,
    editorWidth,
    sidebarCollapsed,
    chatCollapsed,
    isDragging,
    dragTarget,
    toggleSidebar,
    toggleChat,
    startDrag,
    resizeByKeyboard,
  }
}
