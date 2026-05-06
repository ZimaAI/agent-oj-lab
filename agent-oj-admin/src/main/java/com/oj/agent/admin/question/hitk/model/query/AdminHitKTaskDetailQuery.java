package com.oj.agent.admin.question.hitk.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskDetailQuery {

    private Long questionId;

    private Long docId;

    private Long taskId;
}
