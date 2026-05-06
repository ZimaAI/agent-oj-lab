package com.oj.agent.start.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vector.milvus")
public class VectorMilvusProperties {

    private String uri;
    private String token;
    private String collectionName = "algorithm_question_vector";
    private String knowledgeCollectionName = "knowledge_segment_vector";
    private String embeddingField = "embedding";
    private String metricType = "COSINE";

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getKnowledgeCollectionName() {
        return knowledgeCollectionName;
    }

    public void setKnowledgeCollectionName(String knowledgeCollectionName) {
        this.knowledgeCollectionName = knowledgeCollectionName;
    }

    public String getEmbeddingField() {
        return embeddingField;
    }

    public void setEmbeddingField(String embeddingField) {
        this.embeddingField = embeddingField;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }
}
