package com.oj.agent.core.rag.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSegmentRagasTaskListQuery {

    private Long questionId;

    private Long documentId;
}
