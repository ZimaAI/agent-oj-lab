/**
 * 算法题难度枚举
 */
export enum Difficulty {
  EASY = 'EASY',
  MEDIUM = 'MEDIUM',
  HARD = 'HARD'
}

export type AlgorithmQuestionType = 'AI' | 'SYSTEM'

/**
 * 标签
 */
export interface Tag {
  id: number
  tagName: string
}

/**
 * 测试用例执行结果
 */
export interface TestCaseResult {
  passed: boolean
  actualOutput: string | null
  error: string | null
  executionTime?: number
  memoryUsage?: number
}

/**
 * 测试用例值
 */
export type TestCaseValue = string | number | boolean | null | Record<string, unknown> | unknown[]

/**
 * 测试用例
 */
export interface TestCase {
  input: TestCaseValue
  expectedOutput: TestCaseValue
  description?: string | null
  result?: TestCaseResult
}

export interface StandardCase {
  stdin: string
  expectedStdout: string
  publicCase: boolean
  description?: string | null
}

export interface AlgorithmCodeTemplate {
  language: string
  functionName: string | null
  codeSkeleton: string | null
}

/**
 * 算法题
 */
export interface AlgorithmQuestion {
  id: number
  title: string
  description: string
  difficulty: Difficulty | null
  type: AlgorithmQuestionType | null
  standardCasePool: StandardCase[]
  sharedFunctionName: string | null
  sharedCodeSkeleton: string | null
  sharedTestCases: TestCase[]
  codeTemplates: AlgorithmCodeTemplate[]
  // Backward-compatible fields for legacy stream payloads.
  codeSkeleton?: string | null
  testCases?: TestCase[]
  conversationId: string | null
  traceId: string | null
  agentName: string | null
  createTime: string
  updateTime: string
  tags: Tag[]
}

/**
 * 分页数据
 */
export interface IPage<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 算法题查询参数
 */
export interface ProblemQueryParams {
  current?: number
  size?: number
  pageNum?: number
  pageSize?: number
  difficulty?: Difficulty
  type?: AlgorithmQuestionType
  tagIds?: number[]
  keyword?: string
}

/**
 * 单个测试用例执行结果
 */
export interface TestCaseResultVO {
  success: boolean
  result: string | null
  errorMessage: string | null
}

/**
 * 代码执行结果
 */
export interface CodeExecutionResultVO {
  results: TestCaseResultVO[]
}

/**
 * 代码提交记录
 */
export interface CodeSubmissionVO {
  id: number
  algorithmQuestionId: number
  questionTitle: string
  passCount: number
  totalCount: number
  language: string
  createTime: string
}

/**
 * 代码提交评价
 */
export type CodeSubmissionEvaluation = string

/**
 * 代码提交详情
 */
export interface CodeSubmissionDetailVO {
  id: number
  algorithmQuestionId: number
  questionTitle: string
  code: string
  language: string
  testResults: TestCaseResultVO[]
  passCount: number
  totalCount: number
  codeEvaluation: CodeSubmissionEvaluation | null
  createTime: string
}
