/**
 * 单个测试用例的执行结果
 */
export interface TestResult {
  /** 测试用例描述 */
  testCase: string
  /** 是否通过测试 */
  passed: boolean
  /** 程序输出 */
  output: string
  /** 错误信息（如果有） */
  error: string
}

/**
 * 代码评价结果
 *
 * 提供多维度评价：
 * - 正确性分数（0-10）：基于测试通过率
 * - 时间复杂度分数（0-10）：基于算法效率
 * - 空间复杂度分数（0-10）：基于内存使用
 *
 * 包含详细的测试结果、复杂度分析说明，
 * 以及当任意分数低于 8 分时的优化建议
 */
export interface CodeEvaluation {
  /** 正确性分数（0-10） */
  correctnessScore: number
  /** 时间复杂度分数（0-10） */
  timeComplexityScore: number
  /** 空间复杂度分数（0-10） */
  spaceComplexityScore: number
  /** 测试执行结果列表 */
  testResults: TestResult[]
  /** 时间复杂度分析说明 */
  timeComplexityAnalysis: string
  /** 空间复杂度分析说明 */
  spaceComplexityAnalysis: string
  /** 优化建议（当任意分数 < 8 时提供，否则为 null） */
  suggestions: string | null
}
