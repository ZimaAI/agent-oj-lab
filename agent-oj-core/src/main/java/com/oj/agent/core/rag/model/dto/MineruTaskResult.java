package com.oj.agent.core.rag.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MineruTaskResult {

    private String state;

    private String fullZipUrl;

    private String errMsg;

    private String taskId;

    private String batchId;

    private String dataId;
}
