package com.oj.agent.admin.question.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminKnowledgeSegmentPageQuery {

    private Long current;

    private Long size;

    private String keyword;
}
