package com.oj.agent.admin.question.ragas.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRagasEvaluateCommand {

    private Long questionId;

    private Long docId;

    private List<Long> ids;

    private List<Long> ragasIds;

    private List<Long> segmentIds;
}
