/**
 * 消息发送者枚举
 */
export enum MessageSender {
  USER = 'USER',
  AGENT = 'AGENT'
}

/**
 * 聊天消息
 */
export interface ChatMessage {
  id: string
  sender: MessageSender
  content: string
  timestamp: number
}

/**
 * 聊天会话
 */
export interface ChatSession {
  id: string
  problemId: number
  messages: ChatMessage[]
  createdAt: number
  updatedAt: number
}

/**
 * 会话创建响应
 */
export interface ConversationCreateVO {
  conversationId: string
  title: string
  lastMessageTime: string | null
  sessionStatus: string | null
  currentQuestionId: number | null
  createTime: string
}

/**
 * 会话列表项
 */
export interface ConversationListItemVO {
  conversationId: string
  title: string
  lastMessageTime: string | null
  sessionStatus: string | null
  currentQuestionId: number | null
  latestMessagePreview: string | null
  createTime: string
}

/**
 * 会话消息项
 */
export interface ConversationMessageVO {
  id?: number | string
  conversationId?: string
  sender: 'USER' | 'ASSISTANT' | 'AGENT'
  content: string
  messageType?: 'PLAIN' | 'CODE_SUBMISSION' | string
  sequenceNo?: number
  createTime: string
  resultType?: string | null
  resultSummary?: string | null
  resultData?: unknown
}

/**
 * 聊天响应接口
 */
export interface ChatResponse {
  userMessage: ConversationMessageVO
  agentMessage: ConversationMessageVO
}

export type Conversation = ConversationListItemVO
export type ConversationMessage = ConversationMessageVO

/**
 * 会话列表响应接口
 */
export interface ConversationListResponse {
  records: ConversationListItemVO[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 消息列表响应接口
 */
export interface MessageListResponse {
  records: ConversationMessageVO[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 后端 GraphNodeResponse 流式块
 */
export interface WorkflowStreamChunk {
  conservationId: string | null
  nodeName: string | null
  textType: 'TEXT' | 'MARK_DOWN' | 'JSON' | string
  text: string | null
  error: boolean
  complete: boolean
}

/**
 * 兼容旧实现保留的后端节点输出接口
 */
export interface WorkflowNodeOutput {
  conversationId: string
  traceId: string
  agentName: string
  outputKey: string
  outputType: string
  output: unknown
  timestamp: number
}

export interface WorkflowStructuredResultPayload {
  nodeName: string
  raw: WorkflowStreamChunk
  result: unknown
}

export interface WorkflowOverlayPayload {
  text: string
  nodeName: string
  raw: WorkflowStreamChunk
}

/**
 * 助手正文 token 事件
 */
export interface AssistantTokenStreamEvent {
  type: 'assistantToken'
  conversationId: string
  data: string
  timestamp: number
}

/**
 * 浮层文本事件
 */
export interface OverlayTextStreamEvent {
  type: 'overlayText'
  conversationId: string
  data: WorkflowOverlayPayload
  timestamp: number
}

/**
 * 结构化结果事件
 */
export interface StructuredResultStreamEvent {
  type: 'structuredResult'
  conversationId: string
  data: WorkflowStructuredResultPayload
  timestamp: number
}

/**
 * 意图事件
 */
export interface IntentStreamEvent {
  type: 'intent'
  conversationId: string
  data: StreamIntentType
  timestamp: number
}

/**
 * 完成事件
 */
export interface CompleteStreamEvent {
  type: 'complete'
  conversationId: string
  data: unknown
  timestamp: number
}

/**
 * 错误事件
 */
export interface ErrorStreamEvent {
  type: 'error'
  conversationId: string
  data: unknown
  timestamp: number
}

/**
 * 前端归一化流式事件接口
 */
export type ChatStreamEvent =
  | AssistantTokenStreamEvent
  | OverlayTextStreamEvent
  | StructuredResultStreamEvent
  | IntentStreamEvent
  | CompleteStreamEvent
  | ErrorStreamEvent

export type StreamIntentType = 'NEW_QUESTION' | 'CHANGE_DIFFICULT' | 'EVALUATION' | 'OTHER'
