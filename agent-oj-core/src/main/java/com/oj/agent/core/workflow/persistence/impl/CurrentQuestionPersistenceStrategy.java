package com.oj.agent.core.workflow.persistence.impl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.conversation.model.command.ConversationCurrentQuestionUpdateCommand;
import com.oj.agent.core.conversation.model.command.ConversationTitleBackfillCommand;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.CodeQuestionOutputDTO;
import com.oj.agent.core.conversation.service.ConversationService;
import com.oj.agent.core.workflow.model.result.RagJudgeResult;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;
import com.oj.agent.core.workflow.persistence.WorkflowPersistenceStrategy;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentQuestionPersistenceStrategy implements WorkflowPersistenceStrategy {

    private final ConversationService conversationService;

    @Override
    public void persist(OverAllState state, WorkflowPersistenceContext context) {
        String conversationId = StateUtil.getOptionalObjectValueOrNull(state, Constant.CONVERSATION_ID, String.class);
        Integer requestSequenceNo = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.CURRENT_REQUEST_SEQUENCE_NO, Integer.class);
        if (!StringUtils.hasText(conversationId) || requestSequenceNo == null) {
            log.debug("Skip current question persistence: conversationId or requestSequenceNo missing");
            return;
        }

        Long targetQuestionId = resolveTargetQuestionId(state, context);
        if (targetQuestionId == null) {
            log.debug("Skip current question persistence: no target question, conversationId={}", conversationId);
            return;
        }

        String targetTitle = resolveTargetTitle(state);
        AlgorithmQuestionResult currentQuestion = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.CURRENT_ALGORITHM_QUESTION, AlgorithmQuestionResult.class);
        Long currentQuestionId = currentQuestion == null ? null : currentQuestion.getId();
        if (!targetQuestionId.equals(currentQuestionId)) {
            ConversationCurrentQuestionUpdateCommand updateCommand = new ConversationCurrentQuestionUpdateCommand();
            updateCommand.setConversationId(conversationId);
            updateCommand.setRequestSequenceNo(requestSequenceNo);
            updateCommand.setCurrentQuestionId(targetQuestionId);
            boolean updated = conversationService.updateCurrentQuestionIfLatestUserMessageMatches(updateCommand);
            if (!updated) {
                log.info("Skip current question persistence: stale request, conversationId={}, requestSequenceNo={}, targetQuestionId={}",
                        conversationId, requestSequenceNo, targetQuestionId);
            }
        } else {
            log.debug("Skip current question persistence: target question unchanged, conversationId={}, questionId={}",
                    conversationId, targetQuestionId);
        }

        if (!StringUtils.hasText(targetTitle)) {
            log.debug("Skip current question title backfill: title missing, conversationId={}, targetQuestionId={}",
                    conversationId, targetQuestionId);
            return;
        }

        ConversationTitleBackfillCommand titleCommand = new ConversationTitleBackfillCommand();
        titleCommand.setConversationId(conversationId);
        titleCommand.setRequestSequenceNo(requestSequenceNo);
        titleCommand.setTitle(targetTitle);
        boolean titleUpdated = conversationService.backfillTitleIfLatestUserMessageMatches(titleCommand);
        if (!titleUpdated) {
            log.info("Skip current question title backfill: stale request or title already customized, conversationId={}, requestSequenceNo={}, title={}",
                    conversationId, requestSequenceNo, targetTitle);
        }
    }

    @Override
    public int getOrder() {
        return 30;
    }

    // 按约定顺序解析本次请求应回写的题目 id。
    private Long resolveTargetQuestionId(OverAllState state, WorkflowPersistenceContext context) {
        RagJudgeResult ragJudgeOutput = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.RAG_JUDGE_NODE_OUTPUT, RagJudgeResult.class);
        if (ragJudgeOutput != null && ragJudgeOutput.isMatched()
                && ragJudgeOutput.getSelectedQuestion() != null
                && ragJudgeOutput.getSelectedQuestion().getId() != null) {
            return ragJudgeOutput.getSelectedQuestion().getId();
        }

        CodeQuestionOutputDTO codeQuestionOutput = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.CODE_QUESTION_NODE_OUTPUT, CodeQuestionOutputDTO.class);
        if (codeQuestionOutput != null) {
            Long generatedQuestionId = context.getGeneratedQuestionId();
            if (generatedQuestionId != null) {
                return generatedQuestionId;
            }
            return context.get(WorkflowPersistenceContext.GENERATED_QUESTION_ID, Long.class);
        }
        return null;
    }

    // 按约定顺序解析本次请求可回填的会话标题。
    private String resolveTargetTitle(OverAllState state) {
        RagJudgeResult ragJudgeOutput = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.RAG_JUDGE_NODE_OUTPUT, RagJudgeResult.class);
        if (ragJudgeOutput != null && ragJudgeOutput.isMatched()
                && ragJudgeOutput.getSelectedQuestion() != null) {
            return ragJudgeOutput.getSelectedQuestion().getTitle();
        }

        CodeQuestionOutputDTO codeQuestionOutput = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.CODE_QUESTION_NODE_OUTPUT, CodeQuestionOutputDTO.class);
        if (codeQuestionOutput != null && codeQuestionOutput.getQuestion() != null) {
            return codeQuestionOutput.getQuestion().getTitle();
        }
        return null;
    }
}
