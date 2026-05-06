import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(new URL('../QuestionDocumentSegmentsView.vue', import.meta.url), 'utf-8')

describe('question document segments ragas task contract', () => {
  it('submits selection evaluation through ragas task API instead of direct evaluate API', () => {
    const evaluateStart = viewSource.indexOf('const evaluateRagasForSelection = async () => {')
    expect(evaluateStart).toBeGreaterThan(-1)

    const evaluateSnippet = viewSource.slice(evaluateStart, evaluateStart + 900)
    expect(evaluateSnippet).toContain('adminQuestionApi.createQuestionDocumentSegmentRagasTask(questionId, docId, {')
    expect(evaluateSnippet).not.toContain('adminQuestionApi.evaluateQuestionDocumentSegmentRagas(questionId, docId, {')
  })

  it('shows a document-level ragas task history block', () => {
    expect(viewSource).toContain('RAGAS Tasks')
    expect(viewSource).toContain('const loadRagasTasks = async () => {')
    expect(viewSource).toContain('@click="loadRagasTasks"')
  })
})
