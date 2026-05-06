package com.oj.agent.core.question.model.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlgorithmQuestionResponse {

    private Long id;

    private String title;

    private String description;

    private String difficulty;

    private String type;

    private String sharedFunctionName;

    private String sharedCodeSkeleton;

    private String sharedTestCases;

    private List<StandardCaseResponse> standardCasePool;

    private List<AlgorithmCodeTemplateResponse> codeTemplates;

    private String conversationId;

    private String traceId;

    private String agentName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<TagResponse> tags;

    private Double similarity;
}
