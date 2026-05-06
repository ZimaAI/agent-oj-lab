package com.oj.agent.core.question.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmQuestionPageQuery {

    private long pageNum = 1;

    private long pageSize = 20;

    private String difficulty;

    private String type;

    private String keyword;

    private List<Long> tagIds;
}
