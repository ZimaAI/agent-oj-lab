package com.oj.agent.admin.question.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminKnowledgeSegmentPageResult {

    private Long current;

    private Long size;

    private Long total;

    private List<AdminKnowledgeSegmentResult> records;
}
