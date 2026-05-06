package com.oj.agent.admin.question.hitk.model.command;

import lombok.Data;

import java.util.List;

@Data
public class AdminHitKQuestionUpdateCommand {

    private Long questionId;

    private Long docId;

    private List<UpdateItem> updates;

    @Data
    public static class UpdateItem {

        private Long segmentId;

        private String hitKQuestion;
    }
}
