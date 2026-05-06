import { describe, expect, it } from 'vitest'
import { EditorSelection, EditorState } from '@codemirror/state'

import {
  buildPythonCompletionItems,
  createEditorKeyBindings,
  keepCurrentLineIndentOnNewLine,
  mergePromptIntoEditorContent,
  resolveLanguageSupport,
} from '../useCodeEditor'

describe('useCodeEditor extension helpers', () => {
  it('contains core Python completion snippets', () => {
    const items = buildPythonCompletionItems()
    const labels = items.map((item) => item.label)

    expect(labels).toContain('def')
    expect(labels).toContain('for')
    expect(labels).toContain('if')
    expect(labels).toContain('return')
  })

  it('uses snippet apply functions instead of inserting literal placeholders', () => {
    const items = buildPythonCompletionItems()
    const defItem = items.find((item) => item.label === 'def')
    const appendItem = items.find((item) => item.label === 'append')

    expect(typeof defItem?.apply).toBe('function')
    expect(typeof appendItem?.apply).toBe('function')
  })

  it('merges completion text into current editor content', () => {
    expect(mergePromptIntoEditorContent('', 'return value')).toBe('return value')
    expect(mergePromptIntoEditorContent('x = 1', 'return x')).toBe('x = 1\nreturn x')
  })

  it('keeps same indentation when creating a new line', () => {
    const state = EditorState.create({
      doc: '    answer = 42',
      selection: EditorSelection.cursor(15),
    })
    let updatedState: EditorState | null = null

    const handled = keepCurrentLineIndentOnNewLine({
      state,
      dispatch: (transaction) => {
        updatedState = transaction.state
      },
    })

    expect(handled).toBe(true)
    expect(updatedState?.doc.toString()).toBe('    answer = 42\n    ')
    expect(updatedState?.selection.main.head).toBe(20)
  })

  it('registers editor key bindings for tab indentation and aligned newline', () => {
    const keyBindings = createEditorKeyBindings()
    const keys = keyBindings.map((binding) => binding.key)

    expect(keys).toContain('Tab')
    expect(keys).toContain('Shift-Tab')
    expect(keys).toContain('Enter')
    expect(keys).toContain('Shift-Enter')
  })

  it('provides python language support with # line comments', () => {
    const state = EditorState.create({
      doc: 'def solve():\n    return 1',
      extensions: [resolveLanguageSupport('python')],
    })

    const lineComment = (state.languageDataAt('commentTokens', 0)[0] as { line?: string } | undefined)?.line
    expect(lineComment).toBe('#')
  })

  it('provides java language support with // line comments', () => {
    const state = EditorState.create({
      doc: 'class Main { public static void main(String[] args) {} }',
      extensions: [resolveLanguageSupport('java')],
    })

    const lineComment = (state.languageDataAt('commentTokens', 0)[0] as { line?: string } | undefined)?.line
    expect(lineComment).toBe('//')
  })

  it('provides javascript language support with // line comments', () => {
    const state = EditorState.create({
      doc: 'function solve() { return 1 }',
      extensions: [resolveLanguageSupport('javascript')],
    })

    const lineComment = (state.languageDataAt('commentTokens', 0)[0] as { line?: string } | undefined)?.line
    expect(lineComment).toBe('//')
  })
})
