<template>
  <div
    class="panel-resizer"
    :class="{ 'is-dragging': isDragging }"
    role="separator"
    aria-orientation="vertical"
    aria-label="调整面板大小"
    tabindex="0"
    @mousedown="handleMouseDown"
    @keydown.left.prevent="handleKeyboardResize(-10)"
    @keydown.right.prevent="handleKeyboardResize(10)"
  >
    <div class="resizer-handle"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  isDragging?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isDragging: false
})

const emit = defineEmits<{
  dragStart: [event: MouseEvent]
  keyboardResize: [delta: number]
}>()

const handleMouseDown = (event: MouseEvent) => {
  emit('dragStart', event)
}

const handleKeyboardResize = (delta: number) => {
  emit('keyboardResize', delta)
}
</script>

<style scoped>
.panel-resizer {
  width: 6px;
  background-color: var(--color-border);
  cursor: col-resize;
  position: relative;
  flex-shrink: 0;
  transition: background-color 0.2s;
}

.panel-resizer:hover {
  background-color: var(--color-primary);
}

.panel-resizer:focus {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.panel-resizer.is-dragging {
  background-color: var(--color-primary);
}

.resizer-handle {
  position: absolute;
  top: 0;
  left: -4px;
  right: -4px;
  bottom: 0;
}
</style>
