package com.oj.agent.core.evaluation.model.dto;

import lombok.Data;

@Data
public class CodeEvaluationLLMOutputDTO {
    /** 正确性评分 (0-100) */
    private Integer correctnessScore;
    /** 时间复杂度评分 (0-100) */
    private Integer timeComplexityScore;
    /** 空间复杂度评分 (0-100) */
    private Integer spaceComplexityScore;
    /** 综合评分 (0-100) */
    private Integer overallScore;
    /** 时间复杂度分析 */
    private String timeComplexityAnalysis;
    /** 空间复杂度分析 */
    private String spaceComplexityAnalysis;
    /** 代码质量分析 */
    private String codeQualityAnalysis;
    /** 改进建议 */
    private String suggestions;
    /** 评审总结 */
    private String summary;
}
