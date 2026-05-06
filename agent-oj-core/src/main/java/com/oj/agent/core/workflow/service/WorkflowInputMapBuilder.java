package com.oj.agent.core.workflow.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.workflow.model.result.WorkflowBootstrapResult;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


final class WorkflowInputMapBuilder {

    private static final List<String> RESETTABLE_NODE_OUTPUT_KEYS = List.of(
            Constant.QUESTION_REWRITE_NODE_OUTPUT,
            Constant.ANSWER_REWRITE_NODE_OUTPUT,
            Constant.QUESTION_RAG_NODE_OUTPUT,
            Constant.ANSWER_RAG_NODE_OUTPUT,
            Constant.RAG_JUDGE_NODE_OUTPUT,
            Constant.CODE_QUESTION_NODE_OUTPUT,
            Constant.PYTHON_CODE_NODE_OUTPUT,
            Constant.JAVA_CODE_NODE_OUTPUT,
            Constant.JAVASCRIPT_CODE_NODE_OUTPUT,
            Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE_OUTPUT,
            Constant.CODE_EVALUATION_NODE_OUTPUT,
            Constant.MEMORY_COMPRESS_NODE_OUTPUT
    );

    private WorkflowInputMapBuilder() {
    }

    static Map<String, Object> build(String conversationId,
                                     String traceId,
                                     String requestMessage,
                                     Long userId,
                                     WorkflowBootstrapResult bootstrapResult) {
        Objects.requireNonNull(bootstrapResult, "bootstrapResult must not be null");

        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put(Constant.MESSAGES, List.of(new UserMessage(requestMessage)));
        inputMap.put(Constant.CONVERSATION_ID, conversationId);
        inputMap.put(Constant.TRACE_ID, traceId);
        inputMap.put(Constant.USER_ID, userId);
        inputMap.put(Constant.INTENT_RECOGNITION_NODE_OUTPUT, presetIntentStateOrRemoval(bootstrapResult.getPresetIntent()));
        resetNodeOutputs(inputMap);
        inputMap.put(Constant.CURRENT_REQUEST_SEQUENCE_NO, valueOrRemoval(bootstrapResult.getUserMessageSequenceNo()));
        inputMap.put(Constant.CURRENT_ALGORITHM_QUESTION, valueOrRemoval(bootstrapResult.getCurrentAlgorithmQuestionSnapshot()));
        inputMap.put(Constant.CURRENT_CODE_SUBMISSION, valueOrRemoval(bootstrapResult.getCurrentCodeSubmission()));
        return inputMap;
    }

    private static void resetNodeOutputs(Map<String, Object> inputMap) {
        for (String key : RESETTABLE_NODE_OUTPUT_KEYS) {
            inputMap.put(key, OverAllState.MARK_FOR_REMOVAL);
        }
    }

    private static Object presetIntentStateOrRemoval(UserIntentType presetIntent) {
        if (presetIntent == null) {
            return OverAllState.MARK_FOR_REMOVAL;
        }
        return new IntentRecognitionOutputDTO(presetIntent);
    }

    private static Object valueOrRemoval(Object value) {
        if (value == null) {
            return OverAllState.MARK_FOR_REMOVAL;
        }
        return value;
    }
}
