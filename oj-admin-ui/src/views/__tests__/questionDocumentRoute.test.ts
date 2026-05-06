import { describe, expect, it } from 'vitest'
import { buildQuestionDocumentSegmentRoute } from '../questionDocumentRoute'

describe('question document route helper', () => {
  it('builds route with required questionId and docId params', () => {
    const route = buildQuestionDocumentSegmentRoute(1001, 2001, 'Two Sum Doc')
    expect(route).toEqual({
      name: 'admin-question-document-segments',
      params: {
        questionId: 1001,
        docId: 2001,
      },
      query: {
        docTitle: 'Two Sum Doc',
      },
    })
  })

  it('omits docTitle query when title is empty', () => {
    const route = buildQuestionDocumentSegmentRoute(1001, 2001, '   ')
    expect(route).toEqual({
      name: 'admin-question-document-segments',
      params: {
        questionId: 1001,
        docId: 2001,
      },
      query: {},
    })
  })
})
