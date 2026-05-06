package com.oj.agent.core.workflow.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.trace.model.command.TraceCompleteCommand;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.command.TraceStartCommand;
import com.oj.agent.core.trace.model.result.TraceStatusResult;
import com.oj.agent.core.trace.service.WorkflowTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTraceRecorder {

    private final WorkflowTraceService workflowTraceService;

    private final ObjectMapper objectMapper;

    // 启动链路级 trace 记录。
    public void startTrace(String traceId,
                           String conversationId,
                           Long userId,
                           String requestMessage,
                           AlgorithmQuestionResult currentQuestionSnapshot) {
        try {
            workflowTraceService.startTrace(TraceStartCommand.builder()
                    .traceId(traceId)
                    .conversationId(conversationId)
                    .userId(userId)
                    .requestMessage(requestMessage)
                    .currentAlgorithmQuestionId(currentQuestionSnapshot == null ? null : currentQuestionSnapshot.getId())
                    .currentAlgorithmQuestionSnapshot(toQuestionSnapshotJson(currentQuestionSnapshot))
                    .build());
        } catch (Exception e) {
            log.error("Failed to start trace, traceId={}", traceId, e);
        }
    }

    // 标记链路级 trace 成功结束。
    public void completeTrace(String traceId) {
        workflowTraceService.completeTrace(TraceCompleteCommand.builder()
                .traceId(traceId)
                .status(TraceStatusResult.SUCCESS)
                .build());
    }

    // 标记链路级 trace 失败结束。
    public void failTrace(String traceId, String errorMessage) {
        workflowTraceService.completeTrace(TraceCompleteCommand.builder()
                .traceId(traceId)
                .status(TraceStatusResult.FAILED)
                .errorMessage(errorMessage)
                .build());
    }

    // 启动节点级 trace_item 记录并返回主键。
    public Long startTraceItem(TraceItemStartCommand command) {
        return workflowTraceService.startTraceItem(command);
    }

    // 完成节点级 trace_item 记录。
    public void completeTraceItem(TraceItemFinishCommand command) {
        if (command == null) {
            return;
        }
        command.setStatus(TraceStatusResult.SUCCESS);
        workflowTraceService.finishTraceItem(command);
    }

    // 标记节点级 trace_item 记录失败。
    public void failTraceItem(TraceItemFinishCommand command) {
        if (command == null) {
            return;
        }
        command.setStatus(TraceStatusResult.FAILED);
        workflowTraceService.finishTraceItem(command);
    }

    private String toQuestionSnapshotJson(AlgorithmQuestionResult currentQuestionSnapshot) {
        if (currentQuestionSnapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(buildQuestionSnapshotView(currentQuestionSnapshot));
        } catch (Exception e) {
            throw new IllegalStateException("序列化当前题目快照失败", e);
        }
    }

    private Map<String, Object> buildQuestionSnapshotView(AlgorithmQuestionResult currentQuestionSnapshot) {
        Map<String, Object> snapshotView = new LinkedHashMap<>();
        snapshotView.put("id", currentQuestionSnapshot.getId());
        snapshotView.put("title", currentQuestionSnapshot.getTitle());
        snapshotView.put("description", currentQuestionSnapshot.getDescription());
        snapshotView.put("difficulty", currentQuestionSnapshot.getDifficulty());
        snapshotView.put("type", currentQuestionSnapshot.getType());
        snapshotView.put("sharedFunctionName", currentQuestionSnapshot.getSharedFunctionName());
        snapshotView.put("sharedCodeSkeleton", currentQuestionSnapshot.getSharedCodeSkeleton());
        snapshotView.put("sharedTestCases", currentQuestionSnapshot.getSharedTestCases());
        snapshotView.put("codeTemplates", currentQuestionSnapshot.getCodeTemplates());
        snapshotView.put("similarity", currentQuestionSnapshot.getSimilarity());
        return snapshotView;
    }
}
