package com.oj.agent.admin.question.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionDocumentChildSegmentQuery {

    private Long questionId;

    private Long docId;

    private Long parentSegmentId;
}
