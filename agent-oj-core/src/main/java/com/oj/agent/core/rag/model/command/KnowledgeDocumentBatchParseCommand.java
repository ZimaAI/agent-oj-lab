package com.oj.agent.core.rag.model.command;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeDocumentBatchParseCommand {

    private List<KnowledgeDocumentParseCommand> documents = new ArrayList<>();
}
