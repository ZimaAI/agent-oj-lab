package com.oj.agent.admin.question.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionVectorProjectionResponse {

    private String id;

    private Long questionId;

    private String title;

    private String difficulty;

    private String language;

    private List<Float> embedding;

    private Integer isDelete;
}
