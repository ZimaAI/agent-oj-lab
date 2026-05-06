package com.oj.agent.admin.question.hitk.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AdminHitKQuestionUpdateRequest {

    @Valid
    @NotEmpty(message = "updates must not be empty")
    private List<UpdateItem> updates;

    @Data
    public static class UpdateItem {

        @NotNull(message = "segmentId must not be null")
        private Long segmentId;

        @NotBlank(message = "hitkQuestion must not be blank")
        private String hitkQuestion;
    }
}
