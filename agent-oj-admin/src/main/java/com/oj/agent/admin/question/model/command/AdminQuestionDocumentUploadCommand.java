package com.oj.agent.admin.question.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionDocumentUploadCommand {

    private Long questionId;

    private String filename;

    private String contentType;

    private byte[] bytes;
}
