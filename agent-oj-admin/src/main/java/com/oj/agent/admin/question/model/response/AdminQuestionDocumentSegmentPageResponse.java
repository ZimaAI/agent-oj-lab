package com.oj.agent.admin.question.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionDocumentSegmentPageResponse {

    private Long current;

    private Long size;

    private Long total;

    private List<AdminQuestionDocumentSegmentResponse> records;
}
