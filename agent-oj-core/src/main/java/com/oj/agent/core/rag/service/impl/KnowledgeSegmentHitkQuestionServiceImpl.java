package com.oj.agent.core.rag.service.impl;

import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.rag.service.KnowledgeSegmentHitkQuestionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeSegmentHitkQuestionServiceImpl implements KnowledgeSegmentHitkQuestionService {

    private final ChatClient chatClient;

    public KnowledgeSegmentHitkQuestionServiceImpl(AiModelRegistry aiModelRegistry) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
    }

    @Override
    public String generateHitkQuestion(KnowledgeSegment segment) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }
        if (segment == null || !StringUtils.hasText(segment.getText())) {
            throw new IllegalArgumentException("segment text must not be blank");
        }

        // 基于单个知识片段构造命中测试问题。
        String prompt = PromptHelper.buildSegmentHitkQuestionPrompt(segment.getText());
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("generated hitk question is blank");
        }
        return content.trim();
    }
}
