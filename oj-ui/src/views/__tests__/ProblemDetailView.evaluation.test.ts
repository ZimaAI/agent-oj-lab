import { describe, expect, it } from 'vitest'

import {
  resolveEvaluationFromStructuredResultPayload,
  resolveProblemPanelTabFromStructuredResultPayload,
  resolveQuestionDataFromStructuredResultPayload,
} from '../ProblemDetailView.vue'

describe('resolveEvaluationFromStructuredResultPayload', () => {
  it('extracts and normalizes evaluation from codeEvaluationNodeOutput wrapper', () => {
    const evaluation = resolveEvaluationFromStructuredResultPayload({
      nodeName: 'CodeEvaluationNode',
      raw: {
        conservationId: 'c-1',
        nodeName: 'CodeEvaluationNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        codeEvaluationNodeOutput: {
          success: true,
          codeEvaluation: {
            correctnessScore: 10,
            timeComplexityScore: 9,
            spaceComplexityScore: 8,
            timeComplexityAnalysis: 'O(n)',
            spaceComplexityAnalysis: 'O(1)',
            suggestions: null,
            testResults: '{"success":true,"results":[{"passed":true,"output":"3","error":null}]}',
          },
        },
      },
    } as any)

    expect(evaluation).toMatchObject({
      correctnessScore: 10,
      timeComplexityScore: 9,
      spaceComplexityScore: 8,
      testResults: [
        {
          testCase: '测试用例 1',
          passed: true,
          output: '3',
          error: '',
        },
      ],
    })
  })

  it('accepts CodeExecutionNode structured result for evaluation rendering', () => {
    const evaluation = resolveEvaluationFromStructuredResultPayload({
      nodeName: 'CodeExecutionNode',
      raw: {
        conservationId: 'c-2',
        nodeName: 'CodeExecutionNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        success: true,
        codeEvaluation: {
          correctnessScore: 7,
          timeComplexityScore: 7,
          spaceComplexityScore: 6,
          timeComplexityAnalysis: 'O(n log n)',
          spaceComplexityAnalysis: 'O(n)',
          suggestions: 'can optimize constants',
          testResults: [
            {
              success: false,
              result: null,
              errorMessage: 'timeout',
            },
          ],
        },
      },
    } as any)

    expect(evaluation?.testResults).toEqual([
      {
        testCase: '测试用例 1',
        passed: false,
        output: '',
        error: 'timeout',
      },
    ])
  })

  it('returns null when payload has no concrete codeEvaluation body', () => {
    const evaluation = resolveEvaluationFromStructuredResultPayload({
      nodeName: 'CodeEvaluationNode',
      raw: {
        conservationId: 'c-3',
        nodeName: 'CodeEvaluationNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        codeEvaluationNodeOutput: {
          success: true,
          codeEvaluation: null,
        },
      },
    } as any)

    expect(evaluation).toBeNull()
  })
})

describe('resolveProblemPanelTabFromStructuredResultPayload', () => {
  it('returns evaluation tab for CodeEvaluationNode payload', () => {
    const tab = resolveProblemPanelTabFromStructuredResultPayload({
      nodeName: 'CodeEvaluationNode',
      raw: {
        conservationId: 'c-1',
        nodeName: 'CodeEvaluationNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: { codeEvaluationNodeOutput: { success: true, codeEvaluation: {} } },
    } as any)

    expect(tab).toBe('evaluation')
  })

  it('returns evaluation tab for CodeExecutionNode payload', () => {
    const tab = resolveProblemPanelTabFromStructuredResultPayload({
      nodeName: 'CodeExecutionNode',
      raw: {
        conservationId: 'c-2',
        nodeName: 'CodeExecutionNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: { success: true, codeEvaluation: {} },
    } as any)

    expect(tab).toBe('evaluation')
  })

  it('returns description tab for MultiLanguageCodeAssembleNode payload', () => {
    const tab = resolveProblemPanelTabFromStructuredResultPayload({
      nodeName: 'MultiLanguageCodeAssembleNode',
      raw: {
        conservationId: 'c-3',
        nodeName: 'MultiLanguageCodeAssembleNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: { title: 'two-sum' },
    } as any)

    expect(tab).toBe('description')
  })

  it('returns description tab for matched RAGJudgeNode payload', () => {
    const tab = resolveProblemPanelTabFromStructuredResultPayload({
      nodeName: 'RAGJudgeNode',
      raw: {
        conservationId: 'c-4',
        nodeName: 'RAGJudgeNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        is_matched: true,
        selected_question: {
          id: 101,
          title: 'two-sum',
        },
      },
    } as any)

    expect(tab).toBe('description')
  })
})

describe('resolveQuestionDataFromStructuredResultPayload', () => {
  it('returns question payload for MultiLanguageCodeAssembleNode', () => {
    const question = resolveQuestionDataFromStructuredResultPayload({
      nodeName: 'MultiLanguageCodeAssembleNode',
      raw: {
        conservationId: 'c-1',
        nodeName: 'MultiLanguageCodeAssembleNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        id: 102,
        title: 'reverse-string',
      },
    } as any)

    expect(question).toEqual({
      id: 102,
      title: 'reverse-string',
    })
  })

  it('returns selected question when RAGJudgeNode is matched', () => {
    const question = resolveQuestionDataFromStructuredResultPayload({
      nodeName: 'RAGJudgeNode',
      raw: {
        conservationId: 'c-2',
        nodeName: 'RAGJudgeNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        is_matched: true,
        selected_question: {
          id: 103,
          title: 'longest-palindrome',
        },
      },
    } as any)

    expect(question).toEqual({
      id: 103,
      title: 'longest-palindrome',
    })
  })

  it('keeps full RAG selected question payload for description and templates rendering', () => {
    const question = resolveQuestionDataFromStructuredResultPayload({
      nodeName: 'RAGJudgeNode',
      raw: {
        conservationId: 'c-2-1',
        nodeName: 'RAGJudgeNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        is_matched: true,
        selected_question: {
          id: 888,
          title: 'minimum-window-substring',
          description: 'find minimum window',
          difficulty: 'HARD',
          sharedFunctionName: 'minWindow',
          sharedCodeSkeleton: 'def min_window(s, t):\n    pass',
          sharedTestCases: '[{\"input\":{\"s\":\"ADOBECODEBANC\",\"t\":\"ABC\"},\"expectedOutput\":\"BANC\"}]',
          codeTemplates: [
            {
              language: 'PYTHON',
              functionName: 'min_window',
              codeSkeleton: 'def min_window(s, t):\n    pass',
            },
            {
              language: 'JAVA',
              functionName: 'minWindow',
              codeSkeleton: 'class Solution {}',
            },
          ],
        },
      },
    } as any)

    expect(question).toMatchObject({
      id: 888,
      title: 'minimum-window-substring',
      description: 'find minimum window',
      sharedFunctionName: 'minWindow',
      sharedCodeSkeleton: 'def min_window(s, t):\n    pass',
    })
    expect((question as any).codeTemplates).toHaveLength(2)
  })

  it('returns null when RAGJudgeNode does not include selected question', () => {
    const question = resolveQuestionDataFromStructuredResultPayload({
      nodeName: 'RAGJudgeNode',
      raw: {
        conservationId: 'c-3',
        nodeName: 'RAGJudgeNode',
        textType: 'JSON',
        text: '{}',
        error: false,
        complete: false,
      },
      result: {
        is_matched: false,
        selected_question: null,
      },
    } as any)

    expect(question).toBeNull()
  })
})
