export interface IntentOverlayState {
  questionLoading: boolean
  evaluationLoading: boolean
  overlayText: string
  overlayNodeName: string | null
}

export type IntentOverlayEvent =
  | { type: 'submissionStart' }
  | { type: 'intent'; data?: unknown }
  | { type: 'assistantToken' }
  | { type: 'overlayText'; data: { text: string; nodeName: string } }
  | { type: 'structuredResult'; data?: { nodeName?: string | null } }
  | { type: 'complete' | 'error' }

export interface SubmissionStreamFailureHandlers {
  clearOverlay: () => void
  markStreamError: (message: string) => void
}

export const createIntentOverlayState = (): IntentOverlayState => ({
  questionLoading: false,
  evaluationLoading: false,
  overlayText: '',
  overlayNodeName: null,
})

export function nextIntentOverlayState(
  current: IntentOverlayState,
  event: IntentOverlayEvent
): IntentOverlayState {
  if (event.type === 'submissionStart') {
    return {
      questionLoading: false,
      evaluationLoading: true,
      overlayText: '',
      overlayNodeName: null,
    }
  }

  if (event.type === 'intent') {
    const questionLoading = event.data === 'NEW_QUESTION' || event.data === 'CHANGE_DIFFICULT'
    const evaluationLoading = event.data === 'EVALUATION'
    const showOverlay = questionLoading || evaluationLoading

    return {
      questionLoading,
      evaluationLoading,
      overlayText: showOverlay ? current.overlayText : '',
      overlayNodeName: showOverlay ? current.overlayNodeName : null,
    }
  }

  if (event.type === 'overlayText') {
    return {
      questionLoading: current.questionLoading,
      evaluationLoading: current.evaluationLoading,
      overlayText: '',
      overlayNodeName: null,
    }
  }

  if (event.type === 'structuredResult') {
    const nodeName = event.data?.nodeName
    const closeForQuestion =
      current.questionLoading && nodeName === 'MultiLanguageCodeAssembleNode'
    const closeForEvaluation =
      current.evaluationLoading && (nodeName === 'CodeEvaluationNode' || nodeName === 'CodeExecutionNode')

    if (closeForQuestion || closeForEvaluation) {
      return createIntentOverlayState()
    }

    return current
  }

  if (event.type === 'complete' || event.type === 'error') {
    return createIntentOverlayState()
  }

  return current
}

export function handleSubmissionStreamFailure(error: unknown, handlers: SubmissionStreamFailureHandlers) {
  if (error instanceof Error && error.name === 'AbortError') {
    handlers.clearOverlay()
    return false
  }

  const message = error instanceof Error ? error.message : '提交失败'
  handlers.markStreamError(message)
  return true
}
