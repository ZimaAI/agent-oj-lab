package com.oj.agent.admin.question.ragas.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRagasTaskPageResponse {

    private Long current;

    private Long size;

    private Long total;

    private List<AdminRagasTaskResponse> records;
}
