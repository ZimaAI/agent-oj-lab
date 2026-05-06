import { ref, onMounted, onUnmounted, watch, type Ref } from 'vue'
import { Compartment, EditorState } from '@codemirror/state'
import {
  drawSelection,
  EditorView,
  highlightActiveLine,
  highlightActiveLineGutter,
  type KeyBinding,
  keymap,
  lineNumbers,
} from '@codemirror/view'
import {
  defaultKeymap,
  history,
  historyKeymap,
  indentLess,
  insertNewlineKeepIndent,
  insertTab,
} from '@codemirror/commands'
import { python } from '@codemirror/lang-python'
import { java } from '@codemirror/lang-java'
import { javascript } from '@codemirror/lang-javascript'
import {
  autocompletion,
  closeBrackets,
  closeBracketsKeymap,
  snippetCompletion,
  type Completion,
  type CompletionContext,
} from '@codemirror/autocomplete'
import {
  bracketMatching,
  defaultHighlightStyle,
  indentOnInput,
  syntaxHighlighting,
} from '@codemirror/language'
import { oneDark } from '@codemirror/theme-one-dark'

export type CodeEditorLanguage = 'python' | 'java' | 'javascript'

export interface UseCodeEditorOptions {
  initialCode?: string
  language?: CodeEditorLanguage
  theme?: 'light' | 'dark'
  readOnly?: boolean
  problemId?: string | number
  codeSkeleton?: string
}

const PYTHON_COMPLETION_ITEMS: Completion[] = [
  snippetCompletion('def ${1:function_name}(${2:args}):\n    ${3:pass}', {
    label: 'def',
    type: 'keyword',
  }),
  snippetCompletion('class ${1:ClassName}:\n    def __init__(self, ${2:args}):\n        ${3:pass}', {
    label: 'class',
    type: 'keyword',
  }),
  snippetCompletion('for ${1:item} in ${2:iterable}:\n    ${3:pass}', {
    label: 'for',
    type: 'keyword',
  }),
  snippetCompletion('while ${1:condition}:\n    ${2:pass}', {
    label: 'while',
    type: 'keyword',
  }),
  snippetCompletion('if ${1:condition}:\n    ${2:pass}', {
    label: 'if',
    type: 'keyword',
  }),
  snippetCompletion('elif ${1:condition}:\n    ${2:pass}', {
    label: 'elif',
    type: 'keyword',
  }),
  snippetCompletion('else:\n    ${1:pass}', {
    label: 'else',
    type: 'keyword',
  }),
  snippetCompletion('try:\n    ${1:pass}\nexcept ${2:Exception} as ${3:error}:\n    ${4:pass}', {
    label: 'try',
    type: 'keyword',
  }),
  snippetCompletion('except ${1:Exception} as ${2:error}:\n    ${3:pass}', {
    label: 'except',
    type: 'keyword',
  }),
  snippetCompletion('with ${1:context} as ${2:name}:\n    ${3:pass}', {
    label: 'with',
    type: 'keyword',
  }),
  snippetCompletion('return ${1:value}', {
    label: 'return',
    type: 'keyword',
  }),
  snippetCompletion('len(${1:iterable})', {
    label: 'len',
    type: 'function',
  }),
  snippetCompletion('range(${1:start}, ${2:stop})', {
    label: 'range',
    type: 'function',
  }),
  snippetCompletion('enumerate(${1:iterable})', {
    label: 'enumerate',
    type: 'function',
  }),
  snippetCompletion('append(${1:item})', {
    label: 'append',
    type: 'method',
  }),
  snippetCompletion('sort()', {
    label: 'sort',
    type: 'method',
  }),
  snippetCompletion('reverse()', {
    label: 'reverse',
    type: 'method',
  }),
]

export const buildPythonCompletionItems = (): Completion[] => {
  return [...PYTHON_COMPLETION_ITEMS]
}

export const mergePromptIntoEditorContent = (currentCode: string, snippet: string) => {
  const trimmedCode = currentCode.trim()
  if (!trimmedCode) {
    return snippet
  }
  return `${currentCode}\n${snippet}`
}

export const keepCurrentLineIndentOnNewLine = insertNewlineKeepIndent

export const createEditorKeyBindings = (): KeyBinding[] => {
  return [
    { key: 'Tab', run: insertTab, preventDefault: true },
    { key: 'Shift-Tab', run: indentLess, preventDefault: true },
    { key: 'Enter', run: keepCurrentLineIndentOnNewLine, preventDefault: true },
    { key: 'Shift-Enter', run: keepCurrentLineIndentOnNewLine, preventDefault: true },
    ...closeBracketsKeymap,
    ...defaultKeymap,
    ...historyKeymap,
  ]
}

const pythonCompletionSource = (context: CompletionContext) => {
  const token = context.matchBefore(/[\w.]*/)
  if (!token || (token.from === token.to && !context.explicit)) {
    return null
  }

  const from = token.text.lastIndexOf('.') >= 0
    ? token.from + token.text.lastIndexOf('.') + 1
    : token.from

  return {
    from,
    options: buildPythonCompletionItems(),
  }
}

export const resolveLanguageSupport = (language: CodeEditorLanguage) => {
  switch (language) {
    case 'java':
      return java()
    case 'javascript':
      return javascript()
    case 'python':
    default:
      return python()
  }
}

const resolveAutocompleteOverride = (language: CodeEditorLanguage) => {
  if (language === 'python') {
    return [pythonCompletionSource]
  }
  return undefined
}

export const resolveEditorCacheKey = (problemId: string | number, language: CodeEditorLanguage = 'python') => {
  return `code_${problemId}_${language}`
}

const createEditorExtensions = (
  theme: 'light' | 'dark',
  readOnly: boolean,
  editableCompartment: Compartment,
  language: CodeEditorLanguage,
) => {
  const completionOverride = resolveAutocompleteOverride(language)
  const extensions = [
    lineNumbers(),
    highlightActiveLineGutter(),
    history(),
    drawSelection(),
    EditorState.allowMultipleSelections.of(true),
    indentOnInput(),
    bracketMatching(),
    closeBrackets(),
    autocompletion({
      activateOnTyping: true,
      ...(completionOverride ? { override: completionOverride } : {}),
      maxRenderedOptions: 20,
    }),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    highlightActiveLine(),
    keymap.of(createEditorKeyBindings()),
  ]

  extensions.push(resolveLanguageSupport(language))

  if (theme === 'dark') {
    extensions.push(oneDark)
  }

  return [
    ...extensions,
    editableCompartment.of(EditorView.editable.of(!readOnly)),
    EditorView.lineWrapping,
  ]
}

export function useCodeEditor(
  container: Ref<HTMLElement | null>,
  options: UseCodeEditorOptions = {},
  currentProblemId?: Ref<string | number | undefined>,
  currentCodeSkeleton?: Ref<string | undefined>
) {
  const editorView = ref<EditorView | null>(null)
  const code = ref(options.initialCode || '')
  const isReady = ref(false)
  const editableCompartment = new Compartment()

  // Get the current problemId (reactive if provided, otherwise from options)
  const getProblemId = () => currentProblemId?.value ?? options.problemId
  const getLanguage = (): CodeEditorLanguage => options.language ?? 'python'

  // Load code from localStorage if problemId is provided
  const loadPersistedCode = () => {
    const problemId = getProblemId()
    if (problemId) {
      const key = resolveEditorCacheKey(problemId, getLanguage())
      const saved = localStorage.getItem(key)
      if (saved) {
        code.value = saved
        return saved
      }
    }
    // If no saved code, use code skeleton from problem, or fallback to initialCode
    return currentCodeSkeleton?.value ?? options.codeSkeleton ?? options.initialCode ?? ''
  }

  // Check if there's saved code in localStorage
  const hasSavedCode = () => {
    const problemId = getProblemId()
    if (problemId) {
      const key = resolveEditorCacheKey(problemId, getLanguage())
      return localStorage.getItem(key) !== null
    }
    return false
  }

  // Save code to localStorage
  const persistCode = (newCode: string) => {
    const problemId = getProblemId()
    if (problemId) {
      const key = resolveEditorCacheKey(problemId, getLanguage())
      localStorage.setItem(key, newCode)
    }
  }

  // Initialize CodeMirror editor
  const initEditor = () => {
    if (!container.value) return

    const initialCode = loadPersistedCode()
    code.value = initialCode

    const state = EditorState.create({
      doc: initialCode,
      extensions: [
        ...createEditorExtensions(
          options.theme ?? 'light',
          Boolean(options.readOnly),
          editableCompartment,
          options.language ?? 'python',
        ),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            const newCode = update.state.doc.toString()
            code.value = newCode
            persistCode(newCode)
          }
        }),
      ],
    })

    // Create editor view
    editorView.value = new EditorView({
      state,
      parent: container.value,
    })

    isReady.value = true
  }

  // Update theme
  const setTheme = (theme: 'light' | 'dark') => {
    if (!editorView.value) return

    const state = EditorState.create({
      doc: editorView.value.state.doc,
      extensions: [
        ...createEditorExtensions(
          theme,
          Boolean(options.readOnly),
          editableCompartment,
          options.language ?? 'python',
        ),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            const newCode = update.state.doc.toString()
            code.value = newCode
            persistCode(newCode)
          }
        }),
      ],
    })

    editorView.value.setState(state)
  }

  // Get current code
  const getCode = () => {
    return code.value
  }

  // Set code programmatically
  const setCode = (newCode: string) => {
    if (!editorView.value || !isReady.value) return

    // Check if the code is already the same to avoid unnecessary updates
    const currentCode = editorView.value.state.doc.toString()
    if (currentCode === newCode) return

    // Use a single atomic operation to update the editor
    try {
      editorView.value.dispatch({
        changes: {
          from: 0,
          to: editorView.value.state.doc.length,
          insert: newCode,
        },
      })
      code.value = newCode
      persistCode(newCode)
    } catch (error) {
      console.error('Failed to update editor code:', error)
    }
  }

  // Clear persisted code
  const clearPersistedCode = () => {
    const problemId = getProblemId()
    if (problemId) {
      const key = resolveEditorCacheKey(problemId, getLanguage())
      localStorage.removeItem(key)
    }
  }

  // Set read-only state reactively via compartment
  const setReadOnly = (readOnly: boolean) => {
    if (!editorView.value) return
    editorView.value.dispatch({
      effects: editableCompartment.reconfigure(EditorView.editable.of(!readOnly)),
    })
  }

  // Cleanup
  const destroy = () => {
    if (editorView.value) {
      editorView.value.destroy()
      editorView.value = null
      isReady.value = false
    }
  }

  // Watch for container changes
  watch(container, (newContainer) => {
    if (newContainer && !editorView.value) {
      initEditor()
    }
  })

  onMounted(() => {
    if (container.value) {
      initEditor()
    }
  })

  onUnmounted(() => {
    destroy()
  })

  return {
    editorView,
    code,
    isReady,
    setTheme,
    getCode,
    setCode,
    setReadOnly,
    clearPersistedCode,
    destroy,
    hasSavedCode,
    loadPersistedCode,
  }
}
