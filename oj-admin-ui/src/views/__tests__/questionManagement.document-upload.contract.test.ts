import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(new URL('../QuestionManagementView.vue', import.meta.url), 'utf-8')

describe('question management document upload contract', () => {
  it('accepts pdf markdown and zip document formats', () => {
    expect(viewSource).toContain('accept="application/pdf,.md,.markdown,.zip"')
  })

  it('uses generic document upload button label', () => {
    expect(viewSource).toContain("{{ documentUploading ? 'Uploading...' : 'Upload Document' }}")
  })

  it('exposes document delete action and destructive confirmation copy', () => {
    expect(viewSource).toContain('Delete Document')
    expect(viewSource).toContain('Delete this document and all related segments, vectors, RAGAS, and HitK data?')
  })
})
