<script lang="ts">
import { computed, defineComponent, ref, watch } from 'vue'

import JsonTreeNode from './JsonTreeNode.vue'
import { buildDefaultExpandedPaths, parseJsonLikeValue } from './jsonTree'

type JsonTreeViewerModel =
  | { kind: 'empty'; placeholder: '-'; value: null; expandedPaths: Set<string> }
  | { kind: 'text-leaf'; value: string; expandedPaths: Set<string> }
  | { kind: 'json'; value: unknown; expandedPaths: Set<string> }

// 构建树形展示根模型，统一处理空值、纯文本和 JSON 三类输入。
export function buildJsonTreeViewerModel(payload: string | object | null): JsonTreeViewerModel {
  const parsed = parseJsonLikeValue(payload)
  if (parsed.kind === 'empty') {
    return {
      kind: 'empty',
      placeholder: '-',
      value: null,
      expandedPaths: new Set<string>(),
    }
  }

  if (parsed.kind === 'text') {
    return {
      kind: 'text-leaf',
      value: parsed.value,
      expandedPaths: new Set<string>(),
    }
  }

  return {
    kind: 'json',
    value: parsed.value,
    expandedPaths: new Set<string>(buildDefaultExpandedPaths(parsed.value)),
  }
}

export default defineComponent({
  name: 'JsonTreeViewer',
  components: { JsonTreeNode },
  props: {
    value: {
      type: [String, Object] as unknown as () => string | object | null,
      default: null,
    },
  },
  setup(props) {
    // 根据最新输入计算展示模型。
    const model = computed(() => buildJsonTreeViewerModel(props.value))
    // 维护可折叠节点的展开路径集合。
    const expandedPaths = ref<Set<string>>(new Set<string>())

    // 当输入模型变化时，重置展开状态为默认全展开。
    watch(
      model,
      (nextModel) => {
        expandedPaths.value = new Set<string>(nextModel.expandedPaths)
      },
      { immediate: true },
    )

    // 切换单个路径的展开态，使用新 Set 触发响应式更新。
    const togglePath = (path: string): void => {
      const nextPaths = new Set<string>(expandedPaths.value)
      if (nextPaths.has(path)) {
        nextPaths.delete(path)
      } else {
        nextPaths.add(path)
      }
      expandedPaths.value = nextPaths
    }

    return {
      model,
      expandedPaths,
      togglePath,
    }
  },
})
</script>

<template>
  <div class="json-tree-viewer">
    <template v-if="model.kind === 'empty'">
      {{ model.placeholder }}
    </template>
    <JsonTreeNode
      v-else
      :value="model.value"
      :path="'$'"
      :expanded-paths="expandedPaths"
      @toggle-path="togglePath"
    />
  </div>
</template>

<style scoped>
.json-tree-viewer {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--text-dim);
}
</style>
