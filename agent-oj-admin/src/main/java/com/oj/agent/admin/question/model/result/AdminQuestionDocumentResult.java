package com.oj.agent.admin.question.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminQuestionDocumentResult {

    private Long docId;

    private String docTitle;

    private String status;

    private String docUrl;

    private String convertedDocUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer progressPercent;
}
