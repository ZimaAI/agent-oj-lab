import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(new URL('../KnowledgeSegmentHitkTaskView.vue', import.meta.url), 'utf-8')

describe('knowledge segment hitk task selection contract', () => {
  it('keeps selected segment ids across page changes', () => {
    const loadStart = viewSource.indexOf('const loadKnowledgeSegments = async () => {')
    expect(loadStart).toBeGreaterThan(-1)

    const loadSnippet = viewSource.slice(loadStart, loadStart + 1000)
    expect(loadSnippet).not.toContain('selectedSegmentIds.value = selectedSegmentIds.value.filter')
    expect(loadSnippet).toContain('updateSelectedSegmentIds(selectedSegmentIds.value)')
  })

  it('uses selected segment snapshot for cross-page batch actions', () => {
    expect(viewSource).toContain('const selectedSegmentSnapshotMap = ref<Record<number, SegmentRow>>({})')
    expect(viewSource).toContain('const segment = getSelectedSegmentSnapshot(segmentId)')
  })
})
