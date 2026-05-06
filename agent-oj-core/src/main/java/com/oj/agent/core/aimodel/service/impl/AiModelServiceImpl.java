package com.oj.agent.core.aimodel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.core.aimodel.model.command.AiModelConfigCreateCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigDeleteCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigDisableCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigEnableCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigUpdateCommand;
import com.oj.agent.core.aimodel.model.command.AiModelDefaultSetCommand;
import com.oj.agent.core.aimodel.converter.AiModelConverter;
import com.oj.agent.core.aimodel.model.entity.AiModelConfig;
import com.oj.agent.core.aimodel.model.entity.AiModelDefault;
import com.oj.agent.core.aimodel.enums.AiModelProvider;
import com.oj.agent.core.aimodel.enums.AiModelType;
import com.oj.agent.core.aimodel.exception.AiModelException;
import com.oj.agent.core.aimodel.mapper.AiModelConfigMapper;
import com.oj.agent.core.aimodel.mapper.AiModelDefaultMapper;
import com.oj.agent.core.aimodel.model.query.AiModelConfigGetQuery;
import com.oj.agent.core.aimodel.model.query.AiModelConfigListQuery;
import com.oj.agent.core.aimodel.model.result.AiModelConfigCreateResult;
import com.oj.agent.core.aimodel.model.result.AiModelConfigResult;
import com.oj.agent.core.aimodel.model.result.AiModelDefaultsResult;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.aimodel.service.AiModelService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class AiModelServiceImpl implements AiModelService {

    private final AiModelConfigMapper aiModelConfigMapper;

    private final AiModelDefaultMapper aiModelDefaultMapper;

    private final AiModelRegistry aiModelRegistry;

    public AiModelServiceImpl(AiModelConfigMapper aiModelConfigMapper,
                              AiModelDefaultMapper aiModelDefaultMapper,
                              AiModelRegistry aiModelRegistry) {
        this.aiModelConfigMapper = aiModelConfigMapper;
        this.aiModelDefaultMapper = aiModelDefaultMapper;
        this.aiModelRegistry = aiModelRegistry;
    }

    @Override
    public AiModelConfigCreateResult create(AiModelConfigCreateCommand command) {
        String normalizedModelKey = normalizeModelKey(command.getModelKey());
        validateModelType(command.getModelType());
        validateProvider(command.getProvider());
        ensureModelKeyUnique(normalizedModelKey, null);

        AiModelConfig entity = AiModelConverter.toEntity(command);
        entity.setModelKey(normalizedModelKey);
        entity.setIsDelete(0);
        if (entity.getEnabled() == null) {
            entity.setEnabled(0);
        }

        aiModelConfigMapper.insert(entity);
        return AiModelConverter.toCreateResult(entity.getId());
    }

    @Override
    public void update(AiModelConfigUpdateCommand command) {
        AiModelConfig existing = requireModelById(command.getId());
        String normalizedModelKey = normalizeModelKey(command.getModelKey());
        String normalizedModelType = normalizeCode(command.getModelType(), "modelType");
        validateModelType(normalizedModelType);
        validateProvider(command.getProvider());
        if (!Objects.equals(existing.getModelKey(), normalizedModelKey)) {
            throw new AiModelException("modelKey is immutable");
        }
        if (!Objects.equals(existing.getModelType(), normalizedModelType)) {
            throw new AiModelException("modelType is immutable");
        }

        AiModelConverter.apply(command, existing);
        existing.setModelKey(normalizedModelKey);
        aiModelConfigMapper.updateById(existing);

        if (Objects.equals(1, existing.getEnabled())) {
            refreshModelCache(existing.getModelType(), existing.getModelKey());
        }
    }

    @Override
    public AiModelConfigResult getById(AiModelConfigGetQuery query) {
        AiModelConfig entity = requireModelById(query.getId());
        return AiModelConverter.toResult(entity);
    }

    @Override
    public List<AiModelConfigResult> list(AiModelConfigListQuery query) {
        LambdaQueryWrapper<AiModelConfig> queryWrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getIsDelete, 0)
                .orderByDesc(AiModelConfig::getId);

        if (query != null) {
            if (query.getModelType() != null && !query.getModelType().isBlank()) {
                queryWrapper.eq(AiModelConfig::getModelType, query.getModelType().trim());
            }
            if (query.getProvider() != null && !query.getProvider().isBlank()) {
                queryWrapper.eq(AiModelConfig::getProvider, query.getProvider().trim());
            }
            if (query.getEnabled() != null) {
                queryWrapper.eq(AiModelConfig::getEnabled, query.getEnabled());
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                String keyword = query.getKeyword().trim();
                queryWrapper.and(wrapper -> wrapper
                        .like(AiModelConfig::getModelKey, keyword)
                        .or()
                        .like(AiModelConfig::getModelName, keyword));
            }
        }

        return AiModelConverter.toResults(aiModelConfigMapper.selectList(queryWrapper));
    }

    @Override
    public void enable(AiModelConfigEnableCommand command) {
        AiModelConfig entity = requireModelById(command.getId());
        if (Objects.equals(1, entity.getEnabled())) {
            throw new AiModelException("Ai model is already enabled: " + entity.getModelKey());
        }

        entity.setEnabled(1);
        aiModelConfigMapper.updateById(entity);
    }

    @Override
    public void disable(AiModelConfigDisableCommand command) {
        AiModelConfig entity = requireModelById(command.getId());
        if (!Objects.equals(1, entity.getEnabled())) {
            throw new AiModelException("Ai model is already disabled: " + entity.getModelKey());
        }
        if (isDefaultModel(entity.getModelType(), entity.getModelKey())) {
            throw new AiModelException("Cannot disable default ai model: " + entity.getModelKey());
        }

        entity.setEnabled(0);
        aiModelConfigMapper.updateById(entity);
        refreshModelCache(entity.getModelType(), entity.getModelKey());
    }

    @Override
    public void delete(AiModelConfigDeleteCommand command) {
        AiModelConfig entity = requireModelById(command.getId());
        if (isDefaultModel(entity.getModelType(), entity.getModelKey())) {
            throw new AiModelException("Cannot delete default ai model: " + entity.getModelKey());
        }

        entity.setIsDelete(1);
        aiModelConfigMapper.updateById(entity);

        if (Objects.equals(1, entity.getEnabled())) {
            refreshModelCache(entity.getModelType(), entity.getModelKey());
        }
    }

    @Override
    public void setDefaultChatModel(AiModelDefaultSetCommand command) {
        setDefaultModel(AiModelType.CHAT, command.getModelKey());
    }

    @Override
    public void setDefaultEmbeddingModel(AiModelDefaultSetCommand command) {
        setDefaultModel(AiModelType.EMBEDDING, command.getModelKey());
    }

    @Override
    public AiModelDefaultsResult getDefaults() {
        return AiModelConverter.toDefaultsResult(
                getDefaultModelKeyByType(AiModelType.CHAT),
                getDefaultModelKeyByType(AiModelType.EMBEDDING));
    }

    private void setDefaultModel(AiModelType modelType, String modelKey) {
        String normalizedModelKey = normalizeModelKey(modelKey);
        AiModelConfig configEntity = requireModelByModelKey(normalizedModelKey);
        if (!Objects.equals(modelType.name(), configEntity.getModelType())) {
            throw new AiModelException("Ai model type mismatch for key: " + normalizedModelKey);
        }
        if (!Objects.equals(1, configEntity.getEnabled())) {
            throw new AiModelException("Ai model is disabled for key: " + normalizedModelKey);
        }

        LambdaQueryWrapper<AiModelDefault> queryWrapper = new LambdaQueryWrapper<AiModelDefault>()
                .eq(AiModelDefault::getModelType, modelType.name())
                .eq(AiModelDefault::getIsDelete, 0)
                .last("limit 1");
        AiModelDefault defaultEntity = aiModelDefaultMapper.selectOne(queryWrapper);
        if (defaultEntity == null) {
            defaultEntity = new AiModelDefault();
            defaultEntity.setModelType(modelType.name());
            defaultEntity.setModelKey(normalizedModelKey);
            defaultEntity.setIsDelete(0);
            aiModelDefaultMapper.insert(defaultEntity);
        } else {
            defaultEntity.setModelKey(normalizedModelKey);
            aiModelDefaultMapper.updateById(defaultEntity);
        }

        aiModelRegistry.refreshDefaults();
    }

    private AiModelConfig requireModelById(Long id) {
        AiModelConfig entity = aiModelConfigMapper.selectById(id);
        if (entity == null || Objects.equals(1, entity.getIsDelete())) {
            throw new AiModelException("Ai model config not found: " + id);
        }
        return entity;
    }

    private AiModelConfig requireModelByModelKey(String modelKey) {
        LambdaQueryWrapper<AiModelConfig> queryWrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelKey, modelKey)
                .eq(AiModelConfig::getIsDelete, 0)
                .last("limit 1");
        AiModelConfig entity = aiModelConfigMapper.selectOne(queryWrapper);
        if (entity == null) {
            throw new AiModelException("Ai model config not found for key: " + modelKey);
        }
        return entity;
    }

    private void ensureModelKeyUnique(String modelKey, Long excludeId) {
        LambdaQueryWrapper<AiModelConfig> queryWrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelKey, modelKey)
                .eq(AiModelConfig::getIsDelete, 0)
                .last("limit 1");
        AiModelConfig existing = aiModelConfigMapper.selectOne(queryWrapper);

        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw new AiModelException("Ai model key already exists: " + modelKey);
        }
    }

    private boolean isDefaultModel(String modelType, String modelKey) {
        LambdaQueryWrapper<AiModelDefault> queryWrapper = new LambdaQueryWrapper<AiModelDefault>()
                .eq(AiModelDefault::getModelType, modelType)
                .eq(AiModelDefault::getIsDelete, 0)
                .last("limit 1");
        AiModelDefault defaultEntity = aiModelDefaultMapper.selectOne(queryWrapper);
        return defaultEntity != null && Objects.equals(modelKey, defaultEntity.getModelKey());
    }

    private String getDefaultModelKeyByType(AiModelType modelType) {
        LambdaQueryWrapper<AiModelDefault> queryWrapper = new LambdaQueryWrapper<AiModelDefault>()
                .eq(AiModelDefault::getModelType, modelType.name())
                .eq(AiModelDefault::getIsDelete, 0)
                .last("limit 1");
        AiModelDefault defaultEntity = aiModelDefaultMapper.selectOne(queryWrapper);
        if (defaultEntity == null || defaultEntity.getModelKey() == null || defaultEntity.getModelKey().isBlank()) {
            return null;
        }
        return defaultEntity.getModelKey().trim();
    }

    private void refreshModelCache(String modelType, String modelKey) {
        if (Objects.equals(AiModelType.CHAT.name(), modelType)) {
            aiModelRegistry.refreshChatClient(modelKey);
            return;
        }
        if (Objects.equals(AiModelType.EMBEDDING.name(), modelType)) {
            aiModelRegistry.refreshEmbeddingModel(modelKey);
        }
    }

    private String normalizeModelKey(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            throw new AiModelException("modelKey must not be blank");
        }
        return modelKey.trim();
    }

    private String normalizeCode(String code, String fieldName) {
        if (code == null || code.isBlank()) {
            throw new AiModelException(fieldName + " must not be blank");
        }
        return code.trim();
    }

    private void validateModelType(String modelType) {
        try {
            AiModelType.fromCode(normalizeCode(modelType, "modelType"));
        } catch (IllegalArgumentException e) {
            throw new AiModelException(e.getMessage());
        }
    }

    private void validateProvider(String provider) {
        try {
            AiModelProvider.fromCode(normalizeCode(provider, "provider"));
        } catch (IllegalArgumentException e) {
            throw new AiModelException(e.getMessage());
        }
    }
}
