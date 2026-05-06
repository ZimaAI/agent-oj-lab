package com.oj.agent.core.aimodel.service;

import com.oj.agent.core.aimodel.model.command.AiModelConfigCreateCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigDeleteCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigDisableCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigEnableCommand;
import com.oj.agent.core.aimodel.model.command.AiModelConfigUpdateCommand;
import com.oj.agent.core.aimodel.model.command.AiModelDefaultSetCommand;
import com.oj.agent.core.aimodel.model.query.AiModelConfigGetQuery;
import com.oj.agent.core.aimodel.model.query.AiModelConfigListQuery;
import com.oj.agent.core.aimodel.model.result.AiModelConfigCreateResult;
import com.oj.agent.core.aimodel.model.result.AiModelConfigResult;
import com.oj.agent.core.aimodel.model.result.AiModelDefaultsResult;

import java.util.List;

public interface AiModelService {

    AiModelConfigCreateResult create(AiModelConfigCreateCommand command);

    void update(AiModelConfigUpdateCommand command);

    AiModelConfigResult getById(AiModelConfigGetQuery query);

    List<AiModelConfigResult> list(AiModelConfigListQuery query);

    void enable(AiModelConfigEnableCommand command);

    void disable(AiModelConfigDisableCommand command);

    void delete(AiModelConfigDeleteCommand command);

    void setDefaultChatModel(AiModelDefaultSetCommand command);

    void setDefaultEmbeddingModel(AiModelDefaultSetCommand command);

    AiModelDefaultsResult getDefaults();
}
