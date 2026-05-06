package com.oj.agent.core.submission.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmissionPageQuery {

    private long current = 1;

    private long pageSize = 10;

    private Long algorithmQuestionId;
}
