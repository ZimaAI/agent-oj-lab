package com.oj.agent.core.rag.model.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeDocumentBatchParseByUrlRequest {

    private List<KnowledgeDocumentParseByUrlRequest> documents = new ArrayList<>();
}
