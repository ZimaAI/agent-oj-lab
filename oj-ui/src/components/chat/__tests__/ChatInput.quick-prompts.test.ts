import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

import {
  buildQuickPromptItems,
  handleQuickPromptSelectState,
  mergePromptIntoInput,
} from '../ChatInput.vue'

describe('chat input quick prompts helpers', () => {
  it('returns three predefined quick prompt items', () => {
    const items = buildQuickPromptItems()

    expect(items).toHaveLength(3)
    expect(new Set(items).size).toBe(3)
    expect(items.every((item) => item.length > 0)).toBe(true)
  })

  it('fills empty input with selected quick prompt', () => {
    expect(mergePromptIntoInput('', 'Explain the solution strategy')).toBe('Explain the solution strategy')
  })

  it('appends selected quick prompt with newline when input already has content', () => {
    expect(mergePromptIntoInput('Analyze this question first', 'Explain the solution strategy')).toBe(
      'Analyze this question first\nExplain the solution strategy',
    )
  })

  it('returns unchanged state when prompt is empty or input is disabled', () => {
    expect(handleQuickPromptSelectState('existing content', '', false)).toEqual({
      nextInput: 'existing content',
      resetSelectValue: false,
      focusInput: false,
    })

    expect(handleQuickPromptSelectState('existing content', 'Any prompt', true)).toEqual({
      nextInput: 'existing content',
      resetSelectValue: false,
      focusInput: false,
    })
  })

  it('returns merge-and-focus state when a valid prompt is selected', () => {
    expect(handleQuickPromptSelectState('Analyze this question first', 'Explain the solution strategy', false)).toEqual({
      nextInput: 'Analyze this question first\nExplain the solution strategy',
      resetSelectValue: true,
      focusInput: true,
    })
  })

  it('renders quick prompt trigger inside input and floats options above it', () => {
    const source = readFileSync(new URL('../ChatInput.vue', import.meta.url), 'utf-8')

    expect(source).toContain('class="input-shell"')
    expect(source).toContain('class="quick-prompt-anchor"')
    expect(source).toContain('class="quick-prompt-menu"')
    expect(source).toContain('bottom: calc(100% + 8px);')
    expect(source).toContain('position: absolute;')
  })
})
