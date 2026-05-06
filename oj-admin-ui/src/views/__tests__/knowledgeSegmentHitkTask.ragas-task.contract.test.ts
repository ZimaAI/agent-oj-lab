import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(new URL('../KnowledgeSegmentHitkTaskView.vue', import.meta.url), 'utf-8')

describe('knowledge segment ragas task contract', () => {
  it('adds a peer ragas task block that uses the dedicated paging API', () => {
    expect(viewSource).toContain('RAGAS 任务管理')
    expect(viewSource).toContain('const loadRagasTasks = async () => {')
    expect(viewSource).toContain('adminQuestionApi.pageRagasTasks({')
  })

  it('renders nullable ragas averages through score formatting fallback', () => {
    expect(viewSource).toContain('formatScore(task.averageAnswerRelevancy)')
    expect(viewSource).toContain('formatScore(task.averageFaithfulness)')
    expect(viewSource).toContain('formatScore(task.averageContextPrecision)')
    expect(viewSource).toContain('formatScore(task.averageContextRecall)')
  })
})
