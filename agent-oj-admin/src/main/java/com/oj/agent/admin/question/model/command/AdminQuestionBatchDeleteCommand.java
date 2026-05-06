package com.oj.agent.admin.question.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionBatchDeleteCommand {

    private List<Long> questionIds;
}
