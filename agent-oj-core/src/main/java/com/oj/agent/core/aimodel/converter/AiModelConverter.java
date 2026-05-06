package com.oj.agent.core.aimodel.converter;

import com.oj.agent.core.aimodel.model.command.AiModelConfigCreateCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigDeleteCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigDisableCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigEnableCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigUpdateCommand;
import com.oj.agent.core.aimodel.model.command.AiModelDefaultSetCommand;
import com.oj.agent.core.aimodel.model.entity.AiModelConfig;
import com.oj.agent.core.aimodel.model.query.AiModelConfigGetQuery;
import com.oj.agent.core.aimodel.model.query.AiModelConfigListQuery;
import com.oj.agent.core.aimodel.model.request.AiModelConfigCreateRequest;
import com.oj.agent.core.aimodel.model.request.AiModelConfigListRequest;
import com.oj.agent.core.aimodel.model.request.AiModelConfigUpdateRequest;
import com.oj.agent.core.aimodel.response.AiModelConfigResponse;
import com.oj.agent.core.aimodel.response.AiModelDefaultsResponse;
import com.oj.agent.core.aimodel.model.result.AiModelConfigCreateResult;
import com.oj.agent.core.aimodel.model.result.AiModelConfigResult;
import com.oj.agent.core.aimodel.model.result.AiModelDefaultsResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiModelConverter {

    private static final String MASKED_API_KEY = "****";

    private AiModelConverter() {
    }

    public static AiModelConfigCreateCommand toCreateCommand(AiModelConfigCreateRequest request) {
        AiModelConfigCreateCommand command = new AiModelConfigCreateCommand();
        command.setModelKey(request.getModelKey());
        command.setModelName(request.getModelName());
        command.setModelType(request.getModelType());
        command.setProvider(request.getProvider());
        command.setBaseUrl(request.getBaseUrl());
        command.setApiKey(request.getApiKey());
        command.setEnabled(request.getEnabled());
        command.setConfigJson(request.getConfigJson());
        command.setRemark(request.getRemark());
        return command;
    }

    public static AiModelConfigUpdateCommand toUpdateCommand(Long id, AiModelConfigUpdateRequest request) {
        AiModelConfigUpdateCommand command = new AiModelConfigUpdateCommand();
        command.setId(id);
        command.setModelKey(request.getModelKey());
        command.setModelName(request.getModelName());
        command.setModelType(request.getModelType());
        command.setProvider(request.getProvider());
        command.setBaseUrl(request.getBaseUrl());
        command.setApiKey(request.getApiKey());
        command.setEnabled(request.getEnabled());
        command.setConfigJson(request.getConfigJson());
        command.setRemark(request.getRemark());
        return command;
    }

    public static AiModelConfigGetQuery toGetQuery(Long id) {
        AiModelConfigGetQuery query = new AiModelConfigGetQuery();
        query.setId(id);
        return query;
    }

    public static AiModelConfigListQuery toListQuery(AiModelConfigListRequest request) {
        AiModelConfigListQuery query = new AiModelConfigListQuery();
        if (request == null) {
            return query;
        }
        query.setModelType(request.getModelType());
        query.setProvider(request.getProvider());
        query.setEnabled(request.getEnabled());
        query.setKeyword(request.getKeyword());
        return query;
    }

    public static AiModelConfigEnableCommand toEnableCommand(Long id) {
        AiModelConfigEnableCommand command = new AiModelConfigEnableCommand();
        command.setId(id);
        return command;
    }

    public static AiModelConfigDisableCommand toDisableCommand(Long id) {
        AiModelConfigDisableCommand command = new AiModelConfigDisableCommand();
        command.setId(id);
        return command;
    }

    public static AiModelConfigDeleteCommand toDeleteCommand(Long id) {
        AiModelConfigDeleteCommand command = new AiModelConfigDeleteCommand();
        command.setId(id);
        return command;
    }

    public static AiModelDefaultSetCommand toDefaultSetCommand(String modelKey) {
        AiModelDefaultSetCommand command = new AiModelDefaultSetCommand();
        command.setModelKey(modelKey);
        return command;
    }

    public static AiModelConfig toEntity(AiModelConfigCreateCommand command) {
        AiModelConfig entity = new AiModelConfig();
        entity.setModelKey(command.getModelKey());
        entity.setModelName(command.getModelName());
        entity.setModelType(command.getModelType());
        entity.setProvider(command.getProvider());
        entity.setBaseUrl(command.getBaseUrl());
        entity.setApiKey(command.getApiKey());
        entity.setEnabled(command.getEnabled());
        entity.setConfigJson(command.getConfigJson());
        entity.setRemark(command.getRemark());
        return entity;
    }

    public static void apply(AiModelConfigUpdateCommand command, AiModelConfig entity) {
        entity.setModelName(command.getModelName());
        entity.setModelType(command.getModelType());
        entity.setProvider(command.getProvider());
        entity.setBaseUrl(command.getBaseUrl());
        entity.setApiKey(command.getApiKey());
        entity.setEnabled(command.getEnabled());
        entity.setConfigJson(command.getConfigJson());
        entity.setRemark(command.getRemark());
    }

    public static AiModelConfigCreateResult toCreateResult(Long id) {
        AiModelConfigCreateResult result = new AiModelConfigCreateResult();
        result.setId(id);
        return result;
    }

    public static Long toCreatedId(AiModelConfigCreateResult result) {
        return result == null ? null : result.getId();
    }

    public static AiModelConfigResult toResult(AiModelConfig entity) {
        if (entity == null) {
            return null;
        }
        AiModelConfigResult result = new AiModelConfigResult();
        result.setId(entity.getId());
        result.setModelKey(entity.getModelKey());
        result.setModelName(entity.getModelName());
        result.setModelType(entity.getModelType());
        result.setProvider(entity.getProvider());
        result.setBaseUrl(entity.getBaseUrl());
        result.setApiKey(entity.getApiKey());
        result.setEnabled(entity.getEnabled());
        result.setConfigJson(entity.getConfigJson());
        result.setRemark(entity.getRemark());
        result.setCreateTime(entity.getCreateTime());
        result.setUpdateTime(entity.getUpdateTime());
        return result;
    }

    public static List<AiModelConfigResult> toResults(List<AiModelConfig> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiModelConfigResult> results = new ArrayList<>(entities.size());
        for (AiModelConfig entity : entities) {
            results.add(toResult(entity));
        }
        return results;
    }

    public static AiModelDefaultsResult toDefaultsResult(String chatModelKey, String embeddingModelKey) {
        AiModelDefaultsResult result = new AiModelDefaultsResult();
        result.setChatModelKey(chatModelKey);
        result.setEmbeddingModelKey(embeddingModelKey);
        return result;
    }

    public static AiModelConfigResponse toResponse(AiModelConfigResult result) {
        if (result == null) {
            return null;
        }
        AiModelConfigResponse response = new AiModelConfigResponse();
        response.setId(result.getId());
        response.setModelKey(result.getModelKey());
        response.setModelName(result.getModelName());
        response.setModelType(result.getModelType());
        response.setProvider(result.getProvider());
        response.setBaseUrl(result.getBaseUrl());
        response.setApiKey(MASKED_API_KEY);
        response.setEnabled(result.getEnabled());
        response.setConfigJson(result.getConfigJson());
        response.setRemark(result.getRemark());
        response.setCreateTime(result.getCreateTime());
        response.setUpdateTime(result.getUpdateTime());
        return response;
    }

    public static List<AiModelConfigResponse> toResponses(List<AiModelConfigResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiModelConfigResponse> responses = new ArrayList<>(results.size());
        for (AiModelConfigResult result : results) {
            responses.add(toResponse(result));
        }
        return responses;
    }

    public static AiModelDefaultsResponse toResponse(AiModelDefaultsResult result) {
        if (result == null) {
            return null;
        }
        AiModelDefaultsResponse response = new AiModelDefaultsResponse();
        response.setChatModelKey(result.getChatModelKey());
        response.setEmbeddingModelKey(result.getEmbeddingModelKey());
        return response;
    }
}
