/**
 * 编程语言枚举
 */
export enum ProgrammingLanguage {
  PYTHON = 'PYTHON',
  JAVA = 'JAVA',
  JAVASCRIPT = 'JAVASCRIPT'
}

/**
 * 测试结果
 */
export interface TestResult {
  testCaseId: number
  input: string
  expectedOutput: string
  actualOutput: string
  passed: boolean
  executionTime?: number
  memoryUsed?: number
  error?: string
}

/**
 * 代码执行结果
 */
export interface CodeExecutionResult {
  success: boolean
  testResults: TestResult[]
  totalTests: number
  passedTests: number
  failedTests: number
  executionTime?: number
  error?: string
}

/**
 * 代码提交
 */
export interface CodeSubmission {
  id: string
  problemId: number
  userId: number
  code: string
  language: ProgrammingLanguage
  result: CodeExecutionResult
  submittedAt: number
}
