package com.oj.agent.admin.question.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminQuestionDocumentResponse {

    private Long docId;

    private String docTitle;

    private String status;

    private String docUrl;

    private String convertedDocUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer progressPercent;
}
