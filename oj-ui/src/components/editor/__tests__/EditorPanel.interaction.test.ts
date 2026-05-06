import { describe, expect, it } from 'vitest'

import {
  isEditorShortcutKey,
  resolveEditorActionFromKeyboard,
  shouldResetEditorCodeOnContextChange,
} from '../EditorPanel.vue'

describe('resolveEditorActionFromKeyboard', () => {
  it('detects shortcut key chord only for Ctrl/Cmd + Enter', () => {
    expect(isEditorShortcutKey({ key: 'Enter', ctrlKey: true, metaKey: false, shiftKey: false })).toBe(true)
    expect(isEditorShortcutKey({ key: 'Enter', ctrlKey: false, metaKey: true, shiftKey: true })).toBe(true)
    expect(isEditorShortcutKey({ key: 'Enter', ctrlKey: false, metaKey: false, shiftKey: false })).toBe(false)
    expect(isEditorShortcutKey({ key: 'A', ctrlKey: true, metaKey: false, shiftKey: false })).toBe(false)
  })
  it('returns run for Ctrl+Enter or Cmd+Enter', () => {
    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: true, metaKey: false, shiftKey: false },
        false,
      ),
    ).toBe('run')

    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: false, metaKey: true, shiftKey: false },
        false,
      ),
    ).toBe('run')
  })

  it('returns submit for Ctrl/Cmd+Shift+Enter', () => {
    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: true, metaKey: false, shiftKey: true },
        false,
      ),
    ).toBe('submit')

    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: false, metaKey: true, shiftKey: true },
        false,
      ),
    ).toBe('submit')
  })

  it('returns null when editor is readonly', () => {
    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: true, metaKey: false, shiftKey: false },
        true,
      ),
    ).toBeNull()
  })

  it('returns null when run or submit action is currently disabled', () => {
    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: true, metaKey: false, shiftKey: false },
        false,
        { isRunDisabled: true, isSubmitDisabled: false },
      ),
    ).toBeNull()

    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: true, metaKey: false, shiftKey: true },
        false,
        { isRunDisabled: false, isSubmitDisabled: true },
      ),
    ).toBeNull()
  })

  it('returns null for non-shortcut keys', () => {
    expect(
      resolveEditorActionFromKeyboard(
        { key: 'Enter', ctrlKey: false, metaKey: false, shiftKey: false },
        false,
      ),
    ).toBeNull()

    expect(
      resolveEditorActionFromKeyboard(
        { key: 'A', ctrlKey: true, metaKey: false, shiftKey: false },
        false,
      ),
    ).toBeNull()
  })
})

describe('shouldResetEditorCodeOnContextChange', () => {
  it('resets editor when switching from bound problem to unbound session', () => {
    expect(
      shouldResetEditorCodeOnContextChange(
        { problemId: 101, conversationId: 'conv-a' },
        { problemId: undefined, conversationId: 'conv-a' },
      ),
    ).toBe(true)
  })

  it('resets editor when switching between unbound conversations', () => {
    expect(
      shouldResetEditorCodeOnContextChange(
        { problemId: undefined, conversationId: 'conv-a' },
        { problemId: undefined, conversationId: 'conv-b' },
      ),
    ).toBe(true)
  })

  it('does not reset when remaining in same unbound conversation', () => {
    expect(
      shouldResetEditorCodeOnContextChange(
        { problemId: undefined, conversationId: 'conv-a' },
        { problemId: undefined, conversationId: 'conv-a' },
      ),
    ).toBe(false)
  })

  it('does not reset when switching between bound problems', () => {
    expect(
      shouldResetEditorCodeOnContextChange(
        { problemId: 101, conversationId: 'conv-a' },
        { problemId: 202, conversationId: 'conv-b' },
      ),
    ).toBe(false)
  })
})
