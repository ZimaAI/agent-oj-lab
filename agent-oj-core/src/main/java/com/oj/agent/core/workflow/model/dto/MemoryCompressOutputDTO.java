package com.oj.agent.core.workflow.model.dto;

import lombok.Data;

@Data
public class MemoryCompressOutputDTO {
    private String mode;
    private Integer beforeCount;
    private Integer afterCount;
    private boolean summaryGenerated;
    private Integer trimmedCount;
    private String summaryPreview;
}