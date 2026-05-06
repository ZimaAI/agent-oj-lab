<script lang="ts">
import {
  computed,
  defineComponent,
  onBeforeUnmount,
  ref,
  type CSSProperties,
  type PropType,
  watch,
} from 'vue'

import { describeCollapsibleValue, toInlineString } from './jsonTree'
import { renderSafeMarkdownWithCache } from './jsonTreeMarkdown'

export { toInlineString } from './jsonTree'

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Object.prototype.toString.call(value) === '[object Object]'
}

export function isCollapsibleValue(value: unknown): boolean {
  return Array.isArray(value) || isPlainObject(value)
}

export function isLongStringValue(value: unknown, maxLength = 10): boolean {
  return typeof value === 'string' && value.length > maxLength
}

export function resolvePreviewState(currentPath: string | null, nextPath: string): string | null {
  return currentPath === nextPath ? null : nextPath
}

export function resolvePreviewStateOnOutsideClick(
  currentPath: string | null,
  isInside: boolean,
): string | null {
  return isInside ? currentPath : null
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

export default defineComponent({
  name: 'JsonTreeNode',
  props: {
    path: {
      type: String,
      required: true,
    },
    nodeKey: {
      type: String as PropType<string | null>,
      default: null,
    },
    value: {
      type: null as unknown as PropType<unknown>,
      required: true,
    },
    expandedPaths: {
      type: Object as PropType<Set<string>>,
      required: true,
    },
  },
  emits: ['toggle-path'],
  setup(props, { emit }) {
    const rootRef = ref<HTMLElement | null>(null)
    const previewRef = ref<HTMLElement | null>(null)
    const previewPath = ref<string | null>(null)
    const previewAnchorEl = ref<HTMLElement | null>(null)
    const previewAnchorRect = ref<DOMRect | null>(null)
    const previewCache = new Map<string, string>()
    let isPointerDownListening = false
    let isViewportListening = false

    const isExpanded = computed(() => props.expandedPaths.has(props.path))
    const arrayItems = computed(() => (Array.isArray(props.value) ? props.value : []))
    const objectEntries = computed(() =>
      isPlainObject(props.value) ? Object.entries(props.value) : [],
    )

    // 根据触发元素位置计算浮层样式，确保预览悬浮展示且不挤占树结构布局。
    const previewFloatingStyle = computed<CSSProperties>(() => {
      if (!previewAnchorRect.value || typeof window === 'undefined') {
        return {}
      }

      const viewportPadding = 12
      const viewportWidth = window.innerWidth
      const viewportHeight = window.innerHeight
      const preferredWidth = Math.min(560, Math.max(300, viewportWidth * 0.42))
      const width = clamp(preferredWidth, 260, viewportWidth - viewportPadding * 2)
      const maxHeight = clamp(360, 160, viewportHeight - viewportPadding * 2)
      const left = clamp(
        previewAnchorRect.value.left,
        viewportPadding,
        viewportWidth - width - viewportPadding,
      )

      let top = previewAnchorRect.value.bottom + 10
      if (top + maxHeight > viewportHeight - viewportPadding) {
        top = Math.max(viewportPadding, previewAnchorRect.value.top - maxHeight - 10)
      }

      return {
        left: `${Math.round(left)}px`,
        top: `${Math.round(top)}px`,
        width: `${Math.round(width)}px`,
        maxHeight: `${Math.round(maxHeight)}px`,
      }
    })

    const emitTogglePath = (): void => {
      emit('toggle-path', props.path)
    }

    // 记录触发元素位置，供 Teleport 浮层进行 fixed 定位。
    const syncPreviewAnchorFromEvent = (event: Event): void => {
      const target = event.currentTarget
      if (!(target instanceof HTMLElement)) {
        return
      }
      previewAnchorEl.value = target
      previewAnchorRect.value = target.getBoundingClientRect()
    }

    const onPreviewTriggerEnter = (nextPath: string, event: Event): void => {
      syncPreviewAnchorFromEvent(event)
      previewPath.value = nextPath
    }

    const onPreviewTriggerClick = (nextPath: string, event: Event): void => {
      syncPreviewAnchorFromEvent(event)
      previewPath.value = resolvePreviewState(previewPath.value, nextPath)
      if (previewPath.value === null) {
        previewAnchorEl.value = null
        previewAnchorRect.value = null
      }
    }

    const closePreview = (): void => {
      previewPath.value = null
      previewAnchorEl.value = null
      previewAnchorRect.value = null
    }

    const previewHtml = (path: string, content: string): string =>
      renderSafeMarkdownWithCache(previewCache, path, content)

    // 页面滚动或尺寸变化时更新锚点，保证浮层跟随触发文本。
    const handleViewportChange = (): void => {
      if (previewAnchorEl.value) {
        previewAnchorRect.value = previewAnchorEl.value.getBoundingClientRect()
      }
    }

    // 通过根节点与浮层节点共同判断“内部点击”，避免误关闭预览。
    const handleDocumentPointerDown = (event: PointerEvent): void => {
      const target = event.target
      const isInside =
        target instanceof Node
          ? (rootRef.value?.contains(target) ?? false) || (previewRef.value?.contains(target) ?? false)
          : false
      previewPath.value = resolvePreviewStateOnOutsideClick(previewPath.value, isInside)
      if (previewPath.value === null) {
        previewAnchorEl.value = null
        previewAnchorRect.value = null
      }
    }

    // 仅在预览打开期间挂载 pointerdown 监听，减少全局监听常驻。
    const syncPointerDownListener = (shouldListen: boolean): void => {
      if (shouldListen && !isPointerDownListening) {
        document.addEventListener('pointerdown', handleDocumentPointerDown)
        isPointerDownListening = true
        return
      }

      if (!shouldListen && isPointerDownListening) {
        document.removeEventListener('pointerdown', handleDocumentPointerDown)
        isPointerDownListening = false
      }
    }

    // 仅在预览打开期间监听 viewport 变化，确保 fixed 浮层定位稳定。
    const syncViewportListener = (shouldListen: boolean): void => {
      if (shouldListen && !isViewportListening) {
        window.addEventListener('scroll', handleViewportChange, true)
        window.addEventListener('resize', handleViewportChange)
        isViewportListening = true
        return
      }

      if (!shouldListen && isViewportListening) {
        window.removeEventListener('scroll', handleViewportChange, true)
        window.removeEventListener('resize', handleViewportChange)
        isViewportListening = false
      }
    }

    watch(previewPath, (nextPath) => {
      const shouldListen = nextPath !== null
      syncPointerDownListener(shouldListen)
      if (typeof window !== 'undefined') {
        syncViewportListener(shouldListen)
      }
    })

    onBeforeUnmount(() => {
      syncPointerDownListener(false)
      if (typeof window !== 'undefined') {
        syncViewportListener(false)
      }
    })

    return {
      arrayItems,
      closePreview,
      describeCollapsibleValue,
      emitTogglePath,
      isCollapsibleValue,
      isExpanded,
      isLongStringValue,
      objectEntries,
      onPreviewTriggerClick,
      onPreviewTriggerEnter,
      previewFloatingStyle,
      previewHtml,
      previewPath,
      previewRef,
      rootRef,
      toInlineString,
    }
  },
})
</script>

<template>
  <div ref="rootRef" class="json-tree-node">
    <div v-if="isCollapsibleValue(value)" class="json-tree-node__branch">
      <div class="json-tree-node__line">
        <button
          type="button"
          class="json-tree-node__toggle"
          :aria-expanded="isExpanded"
          :aria-label="isExpanded ? '折叠节点' : '展开节点'"
          @click="emitTogglePath"
        >
          <span class="json-tree-node__toggle-icon">{{ isExpanded ? '▾' : '▸' }}</span>
        </button>
        <span v-if="nodeKey !== null" class="json-tree-node__key">{{ nodeKey }}:</span>
        <span v-if="!isExpanded" class="json-tree-node__summary">
          {{ describeCollapsibleValue(value) }}
        </span>
        <span v-else class="json-tree-node__summary">
          {{ Array.isArray(value) ? '[' : '{' }}
        </span>
      </div>

      <div v-if="isExpanded" class="json-tree-node__children">
        <template v-if="Array.isArray(value)">
          <JsonTreeNode
            v-for="(item, index) in arrayItems"
            :key="`${path}[${index}]`"
            :path="`${path}[${index}]`"
            :node-key="String(index)"
            :value="item"
            :expanded-paths="expandedPaths"
            @toggle-path="$emit('toggle-path', $event)"
          />
        </template>
        <template v-else>
          <JsonTreeNode
            v-for="[key, item] in objectEntries"
            :key="`${path}[${JSON.stringify(key)}]`"
            :path="`${path}[${JSON.stringify(key)}]`"
            :node-key="key"
            :value="item"
            :expanded-paths="expandedPaths"
            @toggle-path="$emit('toggle-path', $event)"
          />
        </template>
      </div>

      <div v-if="isExpanded" class="json-tree-node__line json-tree-node__line--end">
        <span class="json-tree-node__summary">{{ Array.isArray(value) ? ']' : '}' }}</span>
      </div>
    </div>

    <div v-else class="json-tree-node__leaf">
      <span v-if="nodeKey !== null" class="json-tree-node__key">{{ nodeKey }}:</span>
      <button
        v-if="isLongStringValue(value)"
        type="button"
        class="json-tree-node__preview-trigger"
        tabindex="0"
        @mouseenter="onPreviewTriggerEnter(path, $event)"
        @focus="onPreviewTriggerEnter(path, $event)"
        @click="onPreviewTriggerClick(path, $event)"
        @keydown.enter.prevent="onPreviewTriggerClick(path, $event)"
        @keydown.space.prevent="onPreviewTriggerClick(path, $event)"
        @keydown.esc.prevent="closePreview"
      >
        {{ toInlineString(value) }}
      </button>
      <span v-else class="json-tree-node__value">{{ typeof value === 'string' ? `"${value}"` : String(value) }}</span>

      <Teleport to="body">
        <div
          v-if="previewPath === path"
          ref="previewRef"
          class="json-tree-node__preview"
          :style="previewFloatingStyle"
          v-html="previewHtml(path, String(value))"
        />
      </Teleport>
    </div>
  </div>
</template>

<style scoped>
.json-tree-node {
  color: var(--text-dim);
  font-family: var(--font-mono);
  font-size: 0.78rem;
  line-height: 1.55;
}

.json-tree-node__line,
.json-tree-node__leaf {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  min-width: 0;
}

.json-tree-node__line--end {
  margin-left: 1.75rem;
}

.json-tree-node__toggle {
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, var(--surface), var(--card));
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex: 0 0 auto;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.json-tree-node__toggle:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.json-tree-node__toggle:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 1px;
}

.json-tree-node__toggle-icon {
  font-size: 0.7rem;
  line-height: 1;
}

.json-tree-node__children {
  margin: 0.2rem 0 0.25rem 0.9rem;
  padding-left: 0.8rem;
  border-left: 1px dashed var(--border);
  display: flex;
  flex-direction: column;
  gap: 0.08rem;
}

.json-tree-node__key {
  color: var(--text-muted);
  word-break: break-word;
}

.json-tree-node__summary {
  color: var(--text-dim);
}

.json-tree-node__value {
  color: var(--primary);
  word-break: break-word;
}

.json-tree-node__preview-trigger {
  border: 0;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
  padding: 0;
  font: inherit;
  text-align: left;
  word-break: break-word;
  max-width: min(56ch, 100%);
  border-bottom: 1px dashed var(--border);
}

.json-tree-node__preview-trigger:hover,
.json-tree-node__preview-trigger:focus-visible {
  color: var(--text-dim);
  border-bottom-color: var(--primary);
}

.json-tree-node__preview {
  position: fixed;
  z-index: 1400;
  overflow: auto;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 12px 30px rgba(2, 6, 23, 0.35);
  padding: 0.75rem 0.9rem;
  color: var(--text-dim);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  backdrop-filter: blur(8px);
}

.json-tree-node__preview :deep(*) {
  margin: 0;
}

.json-tree-node__preview :deep(pre) {
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 640px) {
  .json-tree-node {
    font-size: 0.74rem;
  }

  .json-tree-node__children {
    margin-left: 0.7rem;
    padding-left: 0.65rem;
  }
}
</style>
