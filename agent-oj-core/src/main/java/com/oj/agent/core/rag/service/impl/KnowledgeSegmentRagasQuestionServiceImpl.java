package com.oj.agent.core.rag.service.impl;

import com.oj.agent.core.rag.model.dto.SegmentRagasQaOutputDTO;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.rag.service.KnowledgeSegmentRagasQuestionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeSegmentRagasQuestionServiceImpl implements KnowledgeSegmentRagasQuestionService {

    private final ChatClient chatClient;

    public KnowledgeSegmentRagasQuestionServiceImpl(AiModelRegistry aiModelRegistry) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
    }

    @Override
    public SegmentRagasQaOutputDTO generateRagasQA(KnowledgeSegment segment) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }
        if (segment == null || !StringUtils.hasText(segment.getText())) {
            throw new IllegalArgumentException("segment text must not be blank");
        }

        // 基于单个知识片段构造RAGAS问题与标准答案。
        String prompt = PromptHelper.buildSegmentRagasQaPrompt(segment.getText());
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("generated ragas question or answer is blank");
        }

        BeanOutputConverter<SegmentRagasQaOutputDTO> converter = new BeanOutputConverter<>(SegmentRagasQaOutputDTO.class);
        SegmentRagasQaOutputDTO outputDTO = converter.convert(content);
        if (outputDTO == null || !StringUtils.hasText(outputDTO.getQuestion()) || !StringUtils.hasText(outputDTO.getStandardAnswer())) {
            throw new IllegalStateException("generated ragas question or answer is invalid");
        }
        outputDTO.setQuestion(outputDTO.getQuestion().trim());
        outputDTO.setStandardAnswer(outputDTO.getStandardAnswer().trim());
        return outputDTO;
    }
}
