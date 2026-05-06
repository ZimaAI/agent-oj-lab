package com.oj.agent.admin.question.ragas.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRagasTaskCreateCommand {

    private Long questionId;

    private Long docId;

    private List<Long> segmentIds;
}
