package com.oj.agent.core.aimodel.controller;

import com.oj.agent.common.api.Result;
import com.oj.agent.core.aimodel.converter.AiModelConverter;
import com.oj.agent.core.aimodel.model.request.AiModelConfigCreateRequest;
import com.oj.agent.core.aimodel.model.request.AiModelConfigListRequest;
import com.oj.agent.core.aimodel.model.request.AiModelConfigUpdateRequest;
import com.oj.agent.core.aimodel.response.AiModelConfigResponse;
import com.oj.agent.core.aimodel.response.AiModelDefaultsResponse;
import com.oj.agent.core.aimodel.service.AiModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/models")
public class AiModelController {

    private final AiModelService aiModelService;

    public AiModelController(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody AiModelConfigCreateRequest request) {
        return Result.success(AiModelConverter.toCreatedId(
                aiModelService.create(AiModelConverter.toCreateCommand(request))));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AiModelConfigUpdateRequest request) {
        aiModelService.update(AiModelConverter.toUpdateCommand(id, request));
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<AiModelConfigResponse> getById(@PathVariable Long id) {
        return Result.success(AiModelConverter.toResponse(
                aiModelService.getById(AiModelConverter.toGetQuery(id))));
    }

    @GetMapping
    public Result<List<AiModelConfigResponse>> list(AiModelConfigListRequest request) {
        return Result.success(AiModelConverter.toResponses(
                aiModelService.list(AiModelConverter.toListQuery(request))));
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        aiModelService.enable(AiModelConverter.toEnableCommand(id));
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        aiModelService.disable(AiModelConverter.toDisableCommand(id));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelService.delete(AiModelConverter.toDeleteCommand(id));
        return Result.success();
    }

    @PostMapping("/default/chat/{modelKey}")
    public Result<Void> setDefaultChatModel(@PathVariable String modelKey) {
        aiModelService.setDefaultChatModel(AiModelConverter.toDefaultSetCommand(modelKey));
        return Result.success();
    }

    @PostMapping("/default/embedding/{modelKey}")
    public Result<Void> setDefaultEmbeddingModel(@PathVariable String modelKey) {
        aiModelService.setDefaultEmbeddingModel(AiModelConverter.toDefaultSetCommand(modelKey));
        return Result.success();
    }

    @GetMapping("/defaults")
    public Result<AiModelDefaultsResponse> getDefaults() {
        return Result.success(AiModelConverter.toResponse(aiModelService.getDefaults()));
    }
}
