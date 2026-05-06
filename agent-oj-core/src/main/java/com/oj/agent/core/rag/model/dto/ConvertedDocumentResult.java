package com.oj.agent.core.rag.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ConvertedDocumentResult {

    private String convertedDocUrl;

    private Map<String, Object> extension;
}
