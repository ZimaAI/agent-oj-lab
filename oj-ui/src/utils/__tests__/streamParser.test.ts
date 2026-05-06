import { describe, expect, it, vi } from 'vitest'

import { parseStreamResponse } from '../streamParser'

function createSseResponse(frameText: string) {
  return new Response(frameText, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

describe('parseStreamResponse', () => {
  it('normalizes GraphNodeResponse chunks into overlay, intent, structured-result, assistant and complete events', async () => {
    const chunks: any[] = []
    const response = createSseResponse([
      'data: {"conservationId":"c-1","nodeName":"IntentRecognitionNode","textType":"TEXT","text":"开始分析需求","error":false,"complete":false}',
      '',
      'data: {"conservationId":"c-1","nodeName":"IntentRecognitionNode","textType":"JSON","text":"{\\"intent\\":\\"NEW_QUESTION\\"}","error":false,"complete":false}',
      '',
      'data: {"conservationId":"c-1","nodeName":"CodeQuestionNode","textType":"JSON","text":"{\\"id\\":123,\\"title\\":\\"两数之和\\",\\"testCases\\":[]}","error":false,"complete":false}',
      '',
      'data: {"conservationId":"c-1","nodeName":"OJAssistantNode","textType":"MARK_DOWN","text":"题目生成完成","error":false,"complete":false}',
      '',
      'event: complete',
      'data: {"conservationId":"c-1","nodeName":"complete","textType":"TEXT","text":null,"error":false,"complete":true}',
      '',
    ].join('\n'))

    await parseStreamResponse(response, {
      onChunk: (event) => {
        chunks.push(event)
      },
      onError: vi.fn(),
    })

    expect(chunks).toEqual([
      {
        type: 'overlayText',
        conversationId: 'c-1',
        data: {
          text: '开始分析需求',
          nodeName: 'IntentRecognitionNode',
          raw: {
            conservationId: 'c-1',
            nodeName: 'IntentRecognitionNode',
            textType: 'TEXT',
            text: '开始分析需求',
            error: false,
            complete: false,
          },
        },
        timestamp: expect.any(Number),
      },
      {
        type: 'intent',
        conversationId: 'c-1',
        data: 'NEW_QUESTION',
        timestamp: expect.any(Number),
      },
      {
        type: 'structuredResult',
        conversationId: 'c-1',
        data: {
          nodeName: 'CodeQuestionNode',
          raw: {
            conservationId: 'c-1',
            nodeName: 'CodeQuestionNode',
            textType: 'JSON',
            text: '{"id":123,"title":"两数之和","testCases":[]}',
            error: false,
            complete: false,
          },
          result: {
            id: 123,
            title: '两数之和',
            testCases: [],
          },
        },
        timestamp: expect.any(Number),
      },
      {
        type: 'assistantToken',
        conversationId: 'c-1',
        data: '题目生成完成',
        timestamp: expect.any(Number),
      },
      {
        type: 'complete',
        conversationId: 'c-1',
        data: {
          conservationId: 'c-1',
          nodeName: 'complete',
          textType: 'TEXT',
          text: null,
          error: false,
          complete: true,
        },
        timestamp: expect.any(Number),
      },
    ])
  })

  it('buffers split JSON chunks and emits intent plus structured-result when payloads become complete', async () => {
    const chunks: any[] = []
    const response = createSseResponse([
      'data: {"conservationId":"c-2","nodeName":"IntentRecognitionNode","textType":"JSON","text":"{\\"intent\\":","error":false,"complete":false}',
      '',
      'data: {"conservationId":"c-2","nodeName":"IntentRecognitionNode","textType":"JSON","text":"\\"NEW_QUESTION\\"}","error":false,"complete":false}',
      '',
      'data: {"conservationId":"c-2","nodeName":"CodeQuestionNode","textType":"JSON","text":"{\\"id\\":1","error":false,"complete":false}',
      '',
      'data: {"conservationId":"c-2","nodeName":"CodeQuestionNode","textType":"JSON","text":",\\"title\\":\\"A\\",\\"testCases\\":[]}","error":false,"complete":false}',
      '',
      'event: complete',
      'data: {"conservationId":"c-2","nodeName":"complete","textType":"TEXT","text":null,"error":false,"complete":true}',
      '',
    ].join('\n'))

    await parseStreamResponse(response, {
      onChunk: (event) => {
        chunks.push(event)
      },
      onError: vi.fn(),
    })

    expect(chunks.map((event) => event.type)).toEqual(['intent', 'structuredResult', 'complete'])
    expect(chunks[0]).toMatchObject({
      type: 'intent',
      conversationId: 'c-2',
      data: 'NEW_QUESTION',
    })
    expect(chunks[1]).toMatchObject({
      type: 'structuredResult',
      conversationId: 'c-2',
      data: {
        nodeName: 'CodeQuestionNode',
        result: {
          id: 1,
          title: 'A',
          testCases: [],
        },
      },
    })
  })

  it('normalizes CHANGE_DIFFICULT intent payloads from intent-recognition JSON chunks', async () => {
    const chunks: any[] = []
    const response = createSseResponse([
      'data: {"conservationId":"c-6","nodeName":"IntentRecognitionNode","textType":"JSON","text":"{\\"intent\\":\\"CHANGE_DIFFICULT\\"}","error":false,"complete":false}',
      '',
    ].join('\n'))

    await parseStreamResponse(response, {
      onChunk: (event) => {
        chunks.push(event)
      },
      onError: vi.fn(),
    })

    expect(chunks).toEqual([
      {
        type: 'intent',
        conversationId: 'c-6',
        data: 'CHANGE_DIFFICULT',
        timestamp: expect.any(Number),
      },
    ])
  })

  it('ignores non-TEXT hints from non-OJ nodes', async () => {
    const chunks: any[] = []
    const response = createSseResponse([
      'data: {"conservationId":"c-3","nodeName":"QuestionRewriteNode","textType":"MARK_DOWN","text":"正在改写问题","error":false,"complete":false}',
      '',
    ].join('\n'))

    await parseStreamResponse(response, {
      onChunk: (event) => {
        chunks.push(event)
      },
      onError: vi.fn(),
    })

    expect(chunks).toHaveLength(0)
  })

  it('concatenates multi-line SSE data fields before JSON parsing', async () => {
    const chunks: any[] = []
    const response = createSseResponse([
      'event: message',
      'data: {"conservationId":"c-4","nodeName":"CodeQuestionNode",',
      'data: "textType":"JSON","text":"{\\"id\\":1,\\"title\\":\\"B\\",\\"testCases\\":[]}","error":false,"complete":false}',
      '',
      'event: complete',
      'data: {"conservationId":"c-4","nodeName":"complete","textType":"TEXT","text":null,"error":false,"complete":true}',
      '',
    ].join('\n'))

    await parseStreamResponse(response, {
      onChunk: (event) => {
        chunks.push(event)
      },
      onError: vi.fn(),
    })

    expect(chunks.map((event) => event.type)).toEqual(['structuredResult', 'complete'])
    expect(chunks[0]).toMatchObject({
      type: 'structuredResult',
      conversationId: 'c-4',
      data: {
        nodeName: 'CodeQuestionNode',
        result: {
          id: 1,
          title: 'B',
          testCases: [],
        },
      },
    })
  })

  it('does not emit debug console logs while parsing normal stream chunks', async () => {
    const chunks: any[] = []
    const response = createSseResponse([
      'data: {"conservationId":"c-5","nodeName":"IntentRecognitionNode","textType":"TEXT","text":"hello","error":false,"complete":false}',
      '',
      'event: complete',
      'data: {"conservationId":"c-5","nodeName":"complete","textType":"TEXT","text":null,"error":false,"complete":true}',
      '',
    ].join('\n'))
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})

    try {
      await parseStreamResponse(response, {
        onChunk: (event) => {
          chunks.push(event)
        },
        onError: vi.fn(),
      })

      expect(chunks.map((event) => event.type)).toEqual(['overlayText', 'complete'])
      expect(logSpy).not.toHaveBeenCalled()
    } finally {
      logSpy.mockRestore()
    }
  })
})
