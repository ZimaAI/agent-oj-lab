package com.oj.agent.core.rag.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSegmentRagasTaskPageResult {

    private Long current;

    private Long size;

    private Long total;

    private List<KnowledgeSegmentRagasTaskResult> records;
}
