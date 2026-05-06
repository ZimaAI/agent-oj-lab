import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import { describe, expect, it, vi } from 'vitest'

vi.mock('./JsonTreeNode.vue', () => ({ default: {} }), { virtual: true })
vi.mock('/src/components/json/JsonTreeNode.vue', () => ({ default: {} }), { virtual: true })

import { buildJsonTreeViewerModel } from '../JsonTreeViewer.vue'

describe('JsonTreeViewer model', () => {
  it('builds empty model for empty payload', () => {
    const model = buildJsonTreeViewerModel(null)

    expect(model.kind).toBe('empty')
    expect(model.value).toBeNull()
    expect(model.placeholder).toBe('-')
    expect([...model.expandedPaths]).toEqual([])
  })

  it('builds json model and collects default expanded paths', () => {
    const payload = { profile: { tags: ['x', { deep: true }] } }
    const model = buildJsonTreeViewerModel(payload)

    expect(model.kind).toBe('json')
    expect(model.value).toEqual(payload)
    expect([...model.expandedPaths]).toEqual([
      '$',
      '$["profile"]',
      '$["profile"]["tags"]',
      '$["profile"]["tags"][1]',
    ])
  })

  it('falls back to text-leaf model when parse fails', () => {
    const model = buildJsonTreeViewerModel('{a:1}')

    expect(model.kind).toBe('text-leaf')
    expect(model.value).toBe('{a:1}')
    expect([...model.expandedPaths]).toEqual([])
  })
})

describe('JsonTreeViewer template contract', () => {
  // 读取 SFC 源码，校验模板关键绑定不被改动。
  const source = readFileSync(fileURLToPath(new URL('../JsonTreeViewer.vue', import.meta.url)), 'utf8')

  it('contains root class and recursive node bindings', () => {
    expect(source).toContain('export function buildJsonTreeViewerModel')
    expect(source).toContain('class="json-tree-viewer"')
    expect(source).toContain('<JsonTreeNode')
    expect(source).toContain("import JsonTreeNode from './JsonTreeNode.vue'")
    expect(source).toContain('components: { JsonTreeNode }')
    expect(source).toContain(':path="\'$\'"')
    expect(source).toContain(':expanded-paths="expandedPaths"')
    expect(source).toContain('@toggle-path="togglePath"')
  })
})
