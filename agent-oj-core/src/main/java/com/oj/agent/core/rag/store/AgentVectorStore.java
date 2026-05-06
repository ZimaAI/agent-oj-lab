package com.oj.agent.core.rag.store;

import com.oj.agent.core.rag.model.VectorDocument;
import com.oj.agent.core.rag.model.VectorSearchRequest;
import com.oj.agent.core.rag.model.VectorSearchResult;

import java.util.Optional;

public interface AgentVectorStore {

    void upsert(VectorDocument document, float[] embedding);

    boolean markDeleted(Long questionId, String reason);

    default int deleteByDocumentId(Long documentId, String reason) {
        throw new UnsupportedOperationException("Delete by documentId is not supported");
    }

    Optional<VectorDocument> findByQuestionId(Long questionId);

    VectorSearchResult search(VectorSearchRequest request);

    int clearQuestionVectors();
}
