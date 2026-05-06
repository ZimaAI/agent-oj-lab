package com.oj.agent.admin.question.compensation.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCompensationTaskResult {

    /**
     * 任务 ID。
     */
    private Long taskId;

    /**
     * 题目 ID。
     */
    private Long questionId;

    /**
     * 操作类型。
     */
    private String operationType;

    /**
     * 任务状态。
     */
    private String status;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 重试次数。
     */
    private Integer retryCount;
}
