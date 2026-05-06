package com.oj.agent.core.aimodel.runtime.factory;

import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.aimodel.model.entity.AiModelConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
public class DynamicAiModelFactory {

    public ChatClient createChatClient(AiModelConfig entity) {
        return ChatClient.create(createChatModel(entity));
    }

    public ChatModel createChatModel(AiModelConfig entity) {
        checkBasic(entity);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(StringUtils.hasText(entity.getApiKey()) ? entity.getApiKey() : "")
                .baseUrl(entity.getBaseUrl())
                .build();

        // 构建 OpenAI 对话参数时默认开启 stream usage，同时保留原有动态参数解析。
        Map<String, Object> configMap = parseConfigMap(entity.getConfigJson());
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(entity.getModelName())
                .streamUsage(true);

        Double temperature = readDouble(configMap, "temperature");
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }

        Integer maxTokens = readInteger(configMap, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    public EmbeddingModel createEmbeddingModel(AiModelConfig entity) {
        checkBasic(entity);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(StringUtils.hasText(entity.getApiKey()) ? entity.getApiKey() : "")
                .baseUrl(entity.getBaseUrl())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(entity.getModelName())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    private static void checkBasic(AiModelConfig entity) {
        Assert.notNull(entity, "entity must not be null");
        Assert.hasText(entity.getBaseUrl(), "baseUrl must not be empty");
        Assert.hasText(entity.getModelName(), "modelName must not be empty");
    }

    private Map<String, Object> parseConfigMap(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return Collections.emptyMap();
        }
        try {
            return JsonUtils.getObjectMapper().readValue(configJson, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (IOException e) {
            throw new IllegalArgumentException("configJson is invalid json", e);
        }
    }

    private Double readDouble(Map<String, Object> configMap, String key) {
        Object value = configMap.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private Integer readInteger(Map<String, Object> configMap, String key) {
        Object value = configMap.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
