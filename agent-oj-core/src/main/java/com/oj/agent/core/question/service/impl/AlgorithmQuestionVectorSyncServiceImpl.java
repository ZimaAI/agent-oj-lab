package com.oj.agent.core.question.service.impl;

import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncService;
import com.oj.agent.core.rag.model.VectorDocument;
import com.oj.agent.core.rag.store.AgentVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class AlgorithmQuestionVectorSyncServiceImpl implements AlgorithmQuestionVectorSyncService {

    private static final String SUCCESS_STATUS = "SUCCESS";

    private final EmbeddingModel embeddingModel;
    private final AgentVectorStore agentVectorStore;
    private final int dimension;

    public AlgorithmQuestionVectorSyncServiceImpl(AiModelRegistry aiModelRegistry,
                                                  AgentVectorStore agentVectorStore,
                                                  @Value("${app.vector.embedding.dimension:1024}") int dimension) {
        this.embeddingModel = aiModelRegistry.getDefaultEmbeddingModel();
        this.agentVectorStore = agentVectorStore;
        this.dimension = dimension;
    }

    @Override
    public void sync(AlgorithmQuestion question) {
        // 构造用于向量化的检索文本。
        String embeddingText = buildEmbeddingText(question);
        // 生成嵌入向量并校验维度。
        float[] embedding = generateEmbedding(embeddingText);
        validateDimension(embedding);
        // 将成功投影结果写入 PostgreSQL 向量表。
        upsertVectorProjection(question, embeddingText, embedding);
    }

    String buildEmbeddingText(AlgorithmQuestion question) {
        return String.join("\n",
                "标题: " + defaultString(question.getTitle()),
                "描述: " + defaultString(question.getDescription()),
                "难度: " + defaultString(question.getDifficulty()),
                "语言: " + defaultString(question.getLanguage()),
                "函数名: " + defaultString(question.getFunctionName())
        );
    }

    float[] generateEmbedding(String embeddingText) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(embeddingText));
        if (response == null || response.getResults().isEmpty() || response.getResults().get(0).getOutput() == null) {
            throw new IllegalStateException("Embedding response is empty");
        }
        return response.getResults().get(0).getOutput();
    }

    void validateDimension(float[] embedding) {
        if (embedding.length != dimension) {
            throw new IllegalStateException("embedding dimension mismatch, expected=" + dimension + ", actual=" + embedding.length);
        }
    }

    void upsertVectorProjection(AlgorithmQuestion question, String embeddingText, float[] embedding) {
        Integer isDelete = Objects.requireNonNullElse(question.getIsDelete(), 0);
        question.setVectorSyncStatus(SUCCESS_STATUS);
        question.setVectorSyncErrorMessage(null);

        VectorDocument document = VectorDocument.builder()
                .id(question.getId() == null ? null : String.valueOf(question.getId()))
                .questionId(question.getId())
                .title(question.getTitle())
                .difficulty(question.getDifficulty())
                .language(question.getLanguage())
                .embedding(toEmbeddingList(embedding))
                .deleted(isDelete == 1)
                .build();
        agentVectorStore.upsert(document, embedding);
    }

    List<Float> toEmbeddingList(float[] embedding) {
        List<Float> values = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            values.add(value);
        }
        return values;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
