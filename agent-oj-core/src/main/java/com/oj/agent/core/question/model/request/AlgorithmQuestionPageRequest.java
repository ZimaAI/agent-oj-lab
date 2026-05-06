package com.oj.agent.core.question.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class AlgorithmQuestionPageRequest {

    @JsonAlias("current")
    private long pageNum = 1;

    @JsonAlias("size")
    private long pageSize = 20;

    private String difficulty;

    private String type;

    private String keyword;

    private List<Long> tagIds;
}
