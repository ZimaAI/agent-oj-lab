import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(new URL('../QuestionDocumentSegmentsView.vue', import.meta.url), 'utf-8')

describe('question document segments selection contract', () => {
  it('keeps selected segment ids when changing page', () => {
    const changePageStart = viewSource.indexOf('const changePage = async (nextPage: number) => {')
    expect(changePageStart).toBeGreaterThan(-1)

    const changePageSnippet = viewSource.slice(changePageStart, changePageStart + 400)
    expect(changePageSnippet).not.toContain('clearSelectionForPageChange(nextPage)')
  })
})
