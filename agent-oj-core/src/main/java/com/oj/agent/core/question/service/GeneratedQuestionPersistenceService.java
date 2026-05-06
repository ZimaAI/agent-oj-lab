package com.oj.agent.core.question.service;

import com.oj.agent.core.question.model.command.GeneratedQuestionPersistCommand;

public interface GeneratedQuestionPersistenceService {

    Long persistGeneratedQuestion(GeneratedQuestionPersistCommand command);
}
