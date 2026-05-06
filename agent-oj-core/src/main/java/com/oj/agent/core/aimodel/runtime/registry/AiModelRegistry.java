package com.oj.agent.core.aimodel.runtime.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.core.aimodel.model.entity.AiModelConfig;
import com.oj.agent.core.aimodel.model.entity.AiModelDefault;
import com.oj.agent.core.aimodel.enums.AiModelType;
import com.oj.agent.core.aimodel.runtime.factory.DynamicAiModelFactory;
import com.oj.agent.core.aimodel.mapper.AiModelConfigMapper;
import com.oj.agent.core.aimodel.mapper.AiModelDefaultMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AiModelRegistry {

    private final AiModelConfigMapper aiModelConfigMapper;

    private final AiModelDefaultMapper aiModelDefaultMapper;

    private final DynamicAiModelFactory dynamicAiModelFactory;

    private final ConcurrentMap<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    public AiModelRegistry(AiModelConfigMapper aiModelConfigMapper,
                           AiModelDefaultMapper aiModelDefaultMapper,
                           DynamicAiModelFactory dynamicAiModelFactory) {
        this.aiModelConfigMapper = aiModelConfigMapper;
        this.aiModelDefaultMapper = aiModelDefaultMapper;
        this.dynamicAiModelFactory = dynamicAiModelFactory;
    }

    public ChatClient getChatClient(String modelKey) {
        String normalizedModelKey = normalizeModelKey(modelKey);
        return chatClientCache.computeIfAbsent(normalizedModelKey, this::createChatClient);
    }

    public EmbeddingModel getEmbeddingModel(String modelKey) {
        String normalizedModelKey = normalizeModelKey(modelKey);
        return embeddingModelCache.computeIfAbsent(normalizedModelKey, this::createEmbeddingModel);
    }

    public ChatClient getDefaultChatClient() {
        return getChatClient(resolveDefaultModelKey(AiModelType.CHAT));
    }

    public EmbeddingModel getDefaultEmbeddingModel() {
        return getEmbeddingModel(resolveDefaultModelKey(AiModelType.EMBEDDING));
    }

    public void refreshChatClient(String modelKey) {
        chatClientCache.remove(normalizeModelKey(modelKey));
    }

    public void refreshEmbeddingModel(String modelKey) {
        embeddingModelCache.remove(normalizeModelKey(modelKey));
    }

    public void refreshDefaults() {
        // no-op for now
    }

    private ChatClient createChatClient(String modelKey) {
        AiModelConfig configEntity = loadConfig(modelKey, AiModelType.CHAT);
        return dynamicAiModelFactory.createChatClient(configEntity);
    }

    private EmbeddingModel createEmbeddingModel(String modelKey) {
        AiModelConfig configEntity = loadConfig(modelKey, AiModelType.EMBEDDING);
        return dynamicAiModelFactory.createEmbeddingModel(configEntity);
    }

    private AiModelConfig loadConfig(String modelKey, AiModelType expectedType) {
        LambdaQueryWrapper<AiModelConfig> queryWrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelKey, modelKey)
                .eq(AiModelConfig::getIsDelete, 0)
                .last("limit 1");

        AiModelConfig configEntity = aiModelConfigMapper.selectOne(queryWrapper);
        if (configEntity == null) {
            throw new IllegalArgumentException("Ai model config not found for key: " + modelKey);
        }
        if (!Objects.equals(expectedType.name(), configEntity.getModelType())) {
            throw new IllegalStateException("Ai model type mismatch for key: " + modelKey);
        }
        if (!Objects.equals(1, configEntity.getEnabled())) {
            throw new IllegalStateException("Ai model is disabled for key: " + modelKey);
        }

        return configEntity;
    }

    private String resolveDefaultModelKey(AiModelType modelType) {
        LambdaQueryWrapper<AiModelDefault> queryWrapper = new LambdaQueryWrapper<AiModelDefault>()
                .eq(AiModelDefault::getModelType, modelType.name())
                .eq(AiModelDefault::getIsDelete, 0)
                .last("limit 1");

        AiModelDefault defaultEntity = aiModelDefaultMapper.selectOne(queryWrapper);
        if (defaultEntity == null || defaultEntity.getModelKey() == null || defaultEntity.getModelKey().isBlank()) {
            throw new IllegalStateException("Default model key is not configured for type: " + modelType.name());
        }

        return defaultEntity.getModelKey().trim();
    }

    private String normalizeModelKey(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            throw new IllegalArgumentException("modelKey must not be blank");
        }
        return modelKey.trim();
    }
}
