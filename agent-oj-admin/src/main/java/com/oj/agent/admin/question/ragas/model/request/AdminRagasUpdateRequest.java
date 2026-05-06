package com.oj.agent.admin.question.ragas.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AdminRagasUpdateRequest {

    @Valid
    @NotEmpty(message = "updates must not be empty")
    private List<UpdateItem> updates;

    @Data
    public static class UpdateItem {
        private Long ragasId;

        @NotNull(message = "segmentId must not be null")
        private Long segmentId;

        @NotBlank(message = "question must not be blank")
        private String question;

        @NotBlank(message = "standardAnswer must not be blank")
        private String standardAnswer;
    }
}
