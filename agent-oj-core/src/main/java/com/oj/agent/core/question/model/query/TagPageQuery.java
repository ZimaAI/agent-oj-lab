package com.oj.agent.core.question.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagPageQuery {

    private long current = 1;

    private long pageSize = 20;

    private String keyword;
}
