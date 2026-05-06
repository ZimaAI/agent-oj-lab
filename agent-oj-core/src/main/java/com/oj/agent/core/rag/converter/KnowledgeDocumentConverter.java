package com.oj.agent.core.rag.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.rag.model.command.KnowledgeDocumentBatchParseCommand;
import com.oj.agent.core.rag.model.command.KnowledgeDocumentParseCommand;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.model.query.KnowledgeDocumentRagSearchQuery;
import com.oj.agent.core.rag.model.request.KnowledgeDocumentBatchParseByUrlRequest;
import com.oj.agent.core.rag.model.request.KnowledgeDocumentParseByUrlRequest;
import com.oj.agent.core.rag.model.request.KnowledgeDocumentRagSearchRequest;
import com.oj.agent.core.rag.model.response.KnowledgeDocumentParseResponse;
import com.oj.agent.core.rag.model.response.KnowledgeDocumentResponse;
import com.oj.agent.core.rag.model.response.KnowledgeRagHitResponse;
import com.oj.agent.core.rag.model.result.KnowledgeDocumentParseResult;
import com.oj.agent.core.rag.model.result.KnowledgeDocumentResult;
import com.oj.agent.core.rag.model.result.KnowledgeRagHitResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class KnowledgeDocumentConverter {

    private static final int DEFAULT_TOP_K = 5;

    private KnowledgeDocumentConverter() {
    }

    public static KnowledgeDocumentParseCommand toParseCommand(KnowledgeDocumentParseByUrlRequest request) {
        KnowledgeDocumentParseCommand command = new KnowledgeDocumentParseCommand();
        if (request == null) {
            return command;
        }
        command.setFileUrl(request.getFileUrl());
        command.setDocTitle(request.getDocTitle());
        command.setDescription(request.getDescription());
        command.setAccessibleBy(request.getAccessibleBy());
        command.setKnowledgeBaseType(request.getKnowledgeBaseType());
        return command;
    }

    public static KnowledgeDocumentBatchParseCommand toBatchParseCommand(KnowledgeDocumentBatchParseByUrlRequest request) {
        KnowledgeDocumentBatchParseCommand command = new KnowledgeDocumentBatchParseCommand();
        if (request == null || request.getDocuments() == null || request.getDocuments().isEmpty()) {
            return command;
        }
        command.setDocuments(request.getDocuments().stream()
                .map(KnowledgeDocumentConverter::toParseCommand)
                .toList());
        return command;
    }

    public static KnowledgeDocumentRagSearchQuery toRagSearchQuery(KnowledgeDocumentRagSearchRequest request) {
        KnowledgeDocumentRagSearchQuery query = new KnowledgeDocumentRagSearchQuery();
        if (request == null) {
            query.setTopK(DEFAULT_TOP_K);
            return query;
        }
        query.setQuery(request.getQuery());
        query.setTopK(request.getTopK() == null ? DEFAULT_TOP_K : request.getTopK());
        return query;
    }

    public static KnowledgeDocumentParseResponse toParseResponse(KnowledgeDocumentParseResult result) {
        if (result == null) {
            return null;
        }
        KnowledgeDocumentParseResponse response = new KnowledgeDocumentParseResponse();
        response.setDocId(result.getDocId());
        response.setDocTitle(result.getDocTitle());
        response.setStatus(result.getStatus());
        response.setDocUrl(result.getDocUrl());
        response.setConvertedDocUrl(result.getConvertedDocUrl());
        return response;
    }

    public static List<KnowledgeDocumentParseResponse> toParseResponseList(List<KnowledgeDocumentParseResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(KnowledgeDocumentConverter::toParseResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    public static KnowledgeDocumentResponse toDocumentResponse(KnowledgeDocumentResult result) {
        if (result == null) {
            return null;
        }
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse();
        response.setDocId(result.getDocId());
        response.setDocTitle(result.getDocTitle());
        response.setUploadUser(result.getUploadUser());
        response.setDocUrl(result.getDocUrl());
        response.setConvertedDocUrl(result.getConvertedDocUrl());
        response.setExpireDate(result.getExpireDate());
        response.setStatus(result.getStatus());
        response.setAccessibleBy(result.getAccessibleBy());
        response.setDescription(result.getDescription());
        response.setKnowledgeBaseType(result.getKnowledgeBaseType());
        response.setExtension(result.getExtension());
        response.setCreatedAt(result.getCreatedAt());
        response.setUpdatedAt(result.getUpdatedAt());
        return response;
    }

    public static Page<KnowledgeDocumentResponse> toDocumentResponsePage(Page<KnowledgeDocumentResult> resultPage) {
        Page<KnowledgeDocumentResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(KnowledgeDocumentConverter::toDocumentResponse)
                .filter(Objects::nonNull)
                .toList());
        return responsePage;
    }

    public static KnowledgeRagHitResponse toRagHitResponse(KnowledgeRagHitResult result) {
        if (result == null) {
            return null;
        }
        KnowledgeRagHitResponse response = new KnowledgeRagHitResponse();
        response.setDocId(result.getDocId());
        response.setDocTitle(result.getDocTitle());
        response.setConvertedDocUrl(result.getConvertedDocUrl());
        response.setSegmentId(result.getSegmentId());
        response.setChunkOrder(result.getChunkOrder());
        response.setScore(result.getScore());
        response.setText(result.getText());
        return response;
    }

    public static KnowledgeDocumentParseResult toParseResult(KnowledgeDocument entity) {
        if (entity == null) {
            return null;
        }
        KnowledgeDocumentParseResult result = new KnowledgeDocumentParseResult();
        result.setDocId(entity.getDocId());
        result.setDocTitle(entity.getDocTitle());
        result.setStatus(entity.getStatus());
        result.setDocUrl(entity.getDocUrl());
        result.setConvertedDocUrl(entity.getConvertedDocUrl());
        return result;
    }

    public static KnowledgeDocumentResult toDocumentResult(KnowledgeDocument entity) {
        if (entity == null) {
            return null;
        }
        KnowledgeDocumentResult result = new KnowledgeDocumentResult();
        result.setDocId(entity.getDocId());
        result.setDocTitle(entity.getDocTitle());
        result.setUploadUser(entity.getUploadUser());
        result.setDocUrl(entity.getDocUrl());
        result.setConvertedDocUrl(entity.getConvertedDocUrl());
        result.setExpireDate(entity.getExpireDate());
        result.setStatus(entity.getStatus());
        result.setAccessibleBy(entity.getAccessibleBy());
        result.setDescription(entity.getDescription());
        result.setKnowledgeBaseType(entity.getKnowledgeBaseType());
        result.setExtension(entity.getExtension());
        result.setCreatedAt(entity.getCreatedAt());
        result.setUpdatedAt(entity.getUpdatedAt());
        return result;
    }
}
