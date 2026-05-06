package com.oj.agent.core.question.service;

import java.util.List;

public interface QuestionTagPersistenceService {

    void persistQuestionTags(Long questionId, List<String> rawTagNames);
}
