package com.oj.agent.admin.question.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class AdminQuestionBatchDeleteRequest {

    @NotEmpty(message = "待删除题目 ID 列表不能为空")
    private List<@NotNull(message = "题目 ID 不能为空") @Positive(message = "题目 ID 必须大于 0") Long> questionIds;
}
