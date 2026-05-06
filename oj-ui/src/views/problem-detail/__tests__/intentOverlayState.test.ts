import { describe, expect, it, vi } from 'vitest'

import {
  createIntentOverlayState,
  handleSubmissionStreamFailure,
  nextIntentOverlayState,
} from '../intentOverlayState'

describe('nextIntentOverlayState', () => {
  it('shows evaluation loading immediately when a code submission starts', () => {
    expect(
      nextIntentOverlayState(createIntentOverlayState(), {
        type: 'submissionStart',
      } as any),
    ).toEqual({
      questionLoading: false,
      evaluationLoading: true,
      overlayText: '',
      overlayNodeName: null,
    })
  })

  it('shows question loading for NEW_QUESTION/CHANGE_DIFFICULT and evaluation loading for EVALUATION intent', () => {
    expect(nextIntentOverlayState(createIntentOverlayState(), { type: 'intent', data: 'NEW_QUESTION' })).toEqual({
      questionLoading: true,
      evaluationLoading: false,
      overlayText: '',
      overlayNodeName: null,
    })
    expect(nextIntentOverlayState(createIntentOverlayState(), { type: 'intent', data: 'CHANGE_DIFFICULT' })).toEqual({
      questionLoading: true,
      evaluationLoading: false,
      overlayText: '',
      overlayNodeName: null,
    })
    expect(nextIntentOverlayState(createIntentOverlayState(), { type: 'intent', data: 'EVALUATION' })).toEqual({
      questionLoading: false,
      evaluationLoading: true,
      overlayText: '',
      overlayNodeName: null,
    })
  })

  it('keeps CHANGE_DIFFICULT question overlay until multi-language assemble structured result or complete event', () => {
    const loading = nextIntentOverlayState(createIntentOverlayState(), {
      type: 'intent',
      data: 'CHANGE_DIFFICULT',
    })

    expect(
      nextIntentOverlayState(loading, {
        type: 'structuredResult',
        data: { nodeName: 'IntentRecognitionNode' },
      } as any),
    ).toEqual(loading)

    expect(
      nextIntentOverlayState(loading, {
        type: 'structuredResult',
        data: { nodeName: 'MultiLanguageCodeAssembleNode' },
      } as any),
    ).toEqual(createIntentOverlayState())

    expect(nextIntentOverlayState(loading, { type: 'complete' })).toEqual(createIntentOverlayState())
  })

  it('clears overlay state for other intent values', () => {
    const current = {
      questionLoading: true,
      evaluationLoading: false,
      overlayText: '正在生成题目',
      overlayNodeName: 'code_question_node',
    }

    expect(nextIntentOverlayState(current, { type: 'intent', data: 'OTHER' })).toEqual({
      questionLoading: false,
      evaluationLoading: false,
      overlayText: '',
      overlayNodeName: null,
    })
  })

  it('does not store overlay text in panel overlay state', () => {
    expect(
      nextIntentOverlayState(
        { questionLoading: true, evaluationLoading: false, overlayText: '', overlayNodeName: null },
        { type: 'overlayText', data: { text: '正在生成题目', nodeName: 'code_question_node' } },
      ),
    ).toEqual({
      questionLoading: true,
      evaluationLoading: false,
      overlayText: '',
      overlayNodeName: null,
    })
  })

  it('only clears evaluation overlay when code-evaluation structured result arrives', () => {
    const current = {
      questionLoading: false,
      evaluationLoading: true,
      overlayText: '',
      overlayNodeName: null,
    }

    expect(
      nextIntentOverlayState(current, {
        type: 'structuredResult',
        data: { nodeName: 'MultiLanguageCodeAssembleNode' },
      } as any),
    ).toEqual(current)

    expect(
      nextIntentOverlayState(current, {
        type: 'structuredResult',
        data: { nodeName: 'CodeEvaluationNode' },
      } as any),
    ).toEqual(createIntentOverlayState())

    expect(
      nextIntentOverlayState(current, {
        type: 'structuredResult',
        data: { nodeName: 'CodeExecutionNode' },
      } as any),
    ).toEqual(createIntentOverlayState())
  })

  it('clears overlay on complete and error', () => {
    const current = {
      questionLoading: true,
      evaluationLoading: false,
      overlayText: '正在生成题目',
      overlayNodeName: 'code_question_node',
    }

    expect(nextIntentOverlayState(current, { type: 'complete' })).toEqual(createIntentOverlayState())
    expect(nextIntentOverlayState(current, { type: 'error' })).toEqual(createIntentOverlayState())
  })

  it('preserves state for assistant token events', () => {
    const current = {
      questionLoading: true,
      evaluationLoading: false,
      overlayText: '正在生成题目',
      overlayNodeName: 'code_question_node',
    }

    expect(nextIntentOverlayState(current, { type: 'assistantToken' })).toEqual(current)
  })
})

describe('handleSubmissionStreamFailure', () => {
  it('silently clears overlay for aborted resubmissions', () => {
    const clearOverlay = vi.fn()
    const markStreamError = vi.fn()

    const handled = handleSubmissionStreamFailure(new DOMException('aborted', 'AbortError'), {
      clearOverlay,
      markStreamError,
    })

    expect(handled).toBe(false)
    expect(clearOverlay).toHaveBeenCalledTimes(1)
    expect(markStreamError).not.toHaveBeenCalled()
  })

  it('forwards real submit failures to the error handler', () => {
    const clearOverlay = vi.fn()
    const markStreamError = vi.fn()

    const handled = handleSubmissionStreamFailure(new Error('提交失败'), {
      clearOverlay,
      markStreamError,
    })

    expect(handled).toBe(true)
    expect(clearOverlay).not.toHaveBeenCalled()
    expect(markStreamError).toHaveBeenCalledWith('提交失败')
  })
})
