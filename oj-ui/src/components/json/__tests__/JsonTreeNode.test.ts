import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import { describe, expect, it } from 'vitest'

import {
  isCollapsibleValue,
  isLongStringValue,
  resolvePreviewState,
  resolvePreviewStateOnOutsideClick,
  toInlineString,
} from '../JsonTreeNode.vue'

describe('JsonTreeNode helpers', () => {
  it('treats only object and array values as collapsible', () => {
    expect(isCollapsibleValue({ a: 1 })).toBe(true)
    expect(isCollapsibleValue([1, 2, 3])).toBe(true)
    expect(isCollapsibleValue(null)).toBe(false)
    expect(isCollapsibleValue('text')).toBe(false)
    expect(isCollapsibleValue(1)).toBe(false)
    expect(isCollapsibleValue(false)).toBe(false)
  })

  it('detects long strings by threshold and reuses inline string preview', () => {
    expect(isLongStringValue('1234567890')).toBe(false)
    expect(isLongStringValue('12345678901')).toBe(true)
    expect(isLongStringValue('1234', 3)).toBe(true)
    expect(isLongStringValue(12345678901)).toBe(false)
    expect(toInlineString('12345678901')).toBe('1234567890...')
  })

  it('toggles preview state by path', () => {
    expect(resolvePreviewState(null, '$["detail"]')).toBe('$["detail"]')
    expect(resolvePreviewState('$["detail"]', '$["detail"]')).toBeNull()
    expect(resolvePreviewState('$["detail"]', '$["other"]')).toBe('$["other"]')
  })

  it('clears preview state when clicking outside', () => {
    expect(resolvePreviewStateOnOutsideClick('$["detail"]', true)).toBe('$["detail"]')
    expect(resolvePreviewStateOnOutsideClick('$["detail"]', false)).toBeNull()
    expect(resolvePreviewStateOnOutsideClick(null, false)).toBeNull()
  })
})

describe('JsonTreeNode template contract', () => {
  // 读取 SFC 源码，校验递归与交互关键标记不被改动。
  const source = readFileSync(fileURLToPath(new URL('../JsonTreeNode.vue', import.meta.url)), 'utf8')

  it('contains recursive collapse and preview markers', () => {
    expect(source).toContain('export function isCollapsibleValue')
    expect(source).toContain('export function isLongStringValue')
    expect(source).toContain('export function resolvePreviewState')
    expect(source).toContain('export function resolvePreviewStateOnOutsideClick')
    expect(source).toContain("export { toInlineString } from './jsonTree'")
    expect(source).toContain('v-if="isCollapsibleValue(value)"')
    expect(source).toContain('describeCollapsibleValue(value)')
    expect(source).toContain('<JsonTreeNode')
    expect(source).toContain('tabindex="0"')
    expect(source).toContain('@keydown.enter.prevent')
    expect(source).toContain('@keydown.space.prevent')
    expect(source).toContain('@keydown.esc.prevent')
    expect(source).toContain('@focus="onPreviewTriggerEnter(path, $event)"')
    expect(source).toContain('@click="onPreviewTriggerClick(path, $event)"')
    expect(source).toContain('@mouseenter="onPreviewTriggerEnter(path, $event)"')
    expect(source).toContain('<Teleport to="body">')
    expect(source).toContain(':style="previewFloatingStyle"')
    expect(source).toContain('v-html="previewHtml(path, String(value))"')
    expect(source).toContain('${path}[${index}]')
    expect(source).toContain('${path}[${JSON.stringify(key)}]')
    expect(source).toContain('aria-expanded')
    expect(source).toContain('watch(previewPath')
    expect(source).toContain("document.addEventListener('pointerdown', handleDocumentPointerDown)")
    expect(source).toContain("document.removeEventListener('pointerdown', handleDocumentPointerDown)")
    expect(source).toContain("window.addEventListener('scroll', handleViewportChange, true)")
    expect(source).toContain("window.removeEventListener('scroll', handleViewportChange, true)")
    expect(source).not.toContain('onMounted(() => {')
  })
})
