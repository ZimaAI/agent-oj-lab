package com.oj.agent.core.rag.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSegmentRagasTaskPageQuery {

    private Long current;

    private Long size;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;
}
