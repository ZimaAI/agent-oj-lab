package com.oj.agent.core.workflow.persistence.impl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.conversation.model.command.ConversationMessageCreateCommand;
import com.oj.agent.core.conversation.service.ConversationMessageService;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;
import com.oj.agent.core.workflow.persistence.WorkflowPersistenceStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantMessagePersistenceStrategy implements WorkflowPersistenceStrategy {

    private final ConversationMessageService conversationMessageService;
    private final ObjectMapper objectMapper;

    @Override
    public void persist(OverAllState state, WorkflowPersistenceContext context) {
        String assistantOutput = (String) state.value(Constant.OJ_ASSISTANT_NODE_OUTPUT).orElse(null);
        String conversationId = (String) state.value(Constant.CONVERSATION_ID).orElse(null);
        String traceId = (String) state.value(Constant.TRACE_ID).orElse(null);

        if (!StringUtils.hasText(assistantOutput)) {
            log.debug("Skip assistant message persistence: output is blank");
            return;
        }
        if (!StringUtils.hasText(conversationId)) {
            log.warn("Skip assistant message persistence: conversationId is blank, traceId={}", traceId);
            return;
        }

        ConversationMessageCreateCommand assistantMessage = buildMessage(state, conversationId, assistantOutput, traceId);
        conversationMessageService.createMessage(assistantMessage);

        log.info("Assistant message persisted, conversationId={}, traceId={}", conversationId, traceId);
    }

    @Override
    public int getOrder() {
        return 10;
    }

    private ConversationMessageCreateCommand buildMessage(OverAllState state, String conversationId,
                                                          String assistantOutput, String traceId) {
        ConversationMessageCreateCommand assistantMessage = new ConversationMessageCreateCommand();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setSender("AGENT");
        assistantMessage.setMessageType("PLAIN");
        assistantMessage.setContent(assistantOutput);
        assistantMessage.setTraceId(traceId);

        Object codeQuestionOutput = state.value(Constant.CODE_QUESTION_NODE_OUTPUT).orElse(null);
        if (codeQuestionOutput != null) {
            assistantMessage.setResultType("CODE_QUESTION");
            try {
                assistantMessage.setResultData(objectMapper.writeValueAsString(codeQuestionOutput));
            } catch (Exception e) {
                log.warn("Failed to serialize codeQuestionOutput, traceId={}", traceId, e);
            }
            return assistantMessage;
        }

        Object codeEvaluationOutput = state.value(Constant.CODE_EVALUATION_NODE_OUTPUT).orElse(null);
        if (codeEvaluationOutput != null) {
            assistantMessage.setResultType("CODE_EVALUATION");
            try {
                assistantMessage.setResultData(objectMapper.writeValueAsString(codeEvaluationOutput));
            } catch (Exception e) {
                log.warn("Failed to serialize codeEvaluationOutput, traceId={}", traceId, e);
            }
        }

        return assistantMessage;
    }
}
