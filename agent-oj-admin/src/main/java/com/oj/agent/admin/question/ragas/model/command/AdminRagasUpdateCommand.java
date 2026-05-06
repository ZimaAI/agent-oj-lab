package com.oj.agent.admin.question.ragas.model.command;

import lombok.Data;

import java.util.List;

@Data
public class AdminRagasUpdateCommand {

    private Long questionId;

    private Long docId;

    private List<UpdateItem> updates;

    @Data
    public static class UpdateItem {
        private Long ragasId;
        private Long segmentId;
        private String question;
        private String standardAnswer;
    }
}
