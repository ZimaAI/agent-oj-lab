package com.oj.agent.start.config;

import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.rag.store.AgentVectorStore;
import com.oj.agent.core.rag.store.MilvusAgentVectorStore;
import com.oj.agent.core.rag.store.MilvusKnowledgeSegmentVectorStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.exception.ParamException;
import io.milvus.param.ConnectParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import java.util.Locale;

@Configuration
@EnableConfigurationProperties(VectorMilvusProperties.class)
public class VectorStoreConfiguration {

    @Bean(destroyMethod = "close")
    @Lazy
    public MilvusClientV2 milvusClient(VectorMilvusProperties properties) {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(properties.getUri())
                .token(properties.getToken())
                .build();
        return new MilvusClientV2(connectConfig);
    }

    @Bean(destroyMethod = "close")
    @Lazy
    public MilvusServiceClient milvusServiceClient(VectorMilvusProperties properties) throws ParamException {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withUri(properties.getUri())
                .withToken(properties.getToken())
                .build();
        return new MilvusServiceClient(connectParam);
    }

    @Bean("milvusVectorStore")
    @Lazy
    public VectorStore milvusVectorStore(MilvusServiceClient milvusServiceClient,
                                         AiModelRegistry aiModelRegistry,
                                         VectorMilvusProperties properties,
                                         @Value("${app.vector.embedding.dimension:1024}") int dimension) {
        return MilvusVectorStore.builder(milvusServiceClient, aiModelRegistry.getDefaultEmbeddingModel())
                .collectionName(properties.getCollectionName())
                .embeddingFieldName(properties.getEmbeddingField())
                .metricType(io.milvus.param.MetricType.valueOf(properties.getMetricType().toUpperCase(Locale.ROOT)))
                .embeddingDimension(dimension)
                .initializeSchema(false)
                .build();
    }

    @Bean
    @Lazy
    @Primary
    public AgentVectorStore agentVectorStore(MilvusClientV2 milvusClient, VectorMilvusProperties properties) {
        return new MilvusAgentVectorStore(
                milvusClient,
                properties.getCollectionName(),
                properties.getEmbeddingField(),
                resolveMetricType(properties.getMetricType())
        );
    }

    @Bean("knowledgeSegmentAgentVectorStore")
    @Lazy
    public AgentVectorStore knowledgeSegmentAgentVectorStore(MilvusClientV2 milvusClient, VectorMilvusProperties properties) {
        return new MilvusKnowledgeSegmentVectorStore(
                milvusClient,
                properties.getKnowledgeCollectionName(),
                properties.getEmbeddingField(),
                resolveMetricType(properties.getMetricType())
        );
    }

    private IndexParam.MetricType resolveMetricType(String rawMetricType) {
        if (rawMetricType == null || rawMetricType.trim().isEmpty()) {
            return IndexParam.MetricType.COSINE;
        }
        try {
            return IndexParam.MetricType.valueOf(rawMetricType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return IndexParam.MetricType.COSINE;
        }
    }
}
