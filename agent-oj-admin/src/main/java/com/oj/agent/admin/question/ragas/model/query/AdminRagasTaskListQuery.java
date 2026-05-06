package com.oj.agent.admin.question.ragas.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRagasTaskListQuery {

    private Long questionId;

    private Long docId;
}
