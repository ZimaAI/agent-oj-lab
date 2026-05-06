package com.oj.agent.core.rag.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.rag.model.command.KnowledgeDocumentBatchParseCommand;
import com.oj.agent.core.rag.model.command.KnowledgeDocumentParseCommand;
import com.oj.agent.core.rag.model.result.KnowledgeDocumentParseResult;
import com.oj.agent.core.rag.model.result.KnowledgeDocumentResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService {

    KnowledgeDocumentParseResult parseByUrl(KnowledgeDocumentParseCommand command);

    List<KnowledgeDocumentParseResult> parseByUrlBatch(KnowledgeDocumentBatchParseCommand command);

    KnowledgeDocumentParseResult parseByUpload(MultipartFile file);

    KnowledgeDocumentParseResult parseByUploadAsync(MultipartFile file);

    List<KnowledgeDocumentParseResult> parseByUploadBatch(List<MultipartFile> files);

    Page<KnowledgeDocumentResult> pageCurrentUserDocuments(long pageNum, long pageSize);

    KnowledgeDocumentResult getCurrentUserDocument(Long docId);

}
