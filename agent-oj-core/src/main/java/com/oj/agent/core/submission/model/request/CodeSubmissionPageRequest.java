package com.oj.agent.core.submission.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class CodeSubmissionPageRequest {

    @JsonAlias("pageNum")
    private long current = 1;

    private long pageSize = 10;

    private Long algorithmQuestionId;
}
