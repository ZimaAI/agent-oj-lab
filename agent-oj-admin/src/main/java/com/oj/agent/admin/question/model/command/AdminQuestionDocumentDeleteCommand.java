package com.oj.agent.admin.question.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionDocumentDeleteCommand {

    private Long questionId;

    private Long docId;
}
