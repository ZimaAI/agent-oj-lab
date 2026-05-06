package com.oj.agent.core.trace.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oj.agent.core.trace.model.command.TraceCompleteCommand;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.command.TraceStartCommand;
import com.oj.agent.core.trace.model.entity.Trace;
import com.oj.agent.core.trace.model.entity.TraceItem;
import com.oj.agent.core.trace.mapper.TraceItemMapper;
import com.oj.agent.core.trace.mapper.TraceMapper;
import com.oj.agent.core.trace.model.result.TraceStatusResult;
import com.oj.agent.core.trace.service.WorkflowTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTraceServiceImpl implements WorkflowTraceService {

    private final TraceMapper traceMapper;

    private final TraceItemMapper traceItemMapper;

    @Override
    public void startTrace(TraceStartCommand command) {
        if (command == null) {
            return;
        }
        try {
            Trace trace = new Trace();
            trace.setTraceId(command.getTraceId());
            trace.setConversationId(command.getConversationId());
            trace.setUserId(command.getUserId());
            trace.setRequestMessage(command.getRequestMessage());
            trace.setCurrentAlgorithmQuestionId(command.getCurrentAlgorithmQuestionId());
            trace.setCurrentAlgorithmQuestionSnapshot(command.getCurrentAlgorithmQuestionSnapshot());
            trace.setStatus(TraceStatusResult.RUNNING);
            traceMapper.insert(trace);
        } catch (Exception e) {
            log.error("Failed to start trace, traceId={}", command.getTraceId(), e);
        }
    }

    @Override
    public void completeTrace(TraceCompleteCommand command) {
        if (command == null || command.getTraceId() == null) {
            return;
        }
        try {
            Trace trace = new Trace();
            trace.setStatus(command.getStatus());
            trace.setErrorMessage(command.getErrorMessage());
            traceMapper.update(trace, Wrappers.<Trace>lambdaUpdate().eq(Trace::getTraceId, command.getTraceId()));
        } catch (Exception e) {
            log.error("Failed to update trace, traceId={}", command.getTraceId(), e);
        }
    }

    @Override
    public Long startTraceItem(TraceItemStartCommand command) {
        if (command == null) {
            return null;
        }
        try {
            TraceItem item = new TraceItem();
            item.setTraceId(command.getTraceId());
            item.setNodeName(command.getNodeName());
            item.setItemType(command.getItemType());
            item.setItemKey(command.getItemKey());
            item.setRoundNo(command.getRoundNo());
            item.setToolName(command.getToolName());
            item.setInputPayload(command.getInputPayload());
            item.setInputSummary(command.getInputSummary());
            item.setStartTimestamp(command.getStartTimestamp());
            item.setStatus(TraceStatusResult.RUNNING);
            traceItemMapper.insert(item);
            return item.getId();
        } catch (Exception e) {
            log.error("Failed to start trace item, traceId={}, nodeName={}, itemKey={}",
                    command.getTraceId(), command.getNodeName(), command.getItemKey(), e);
            return null;
        }
    }

    @Override
    public void finishTraceItem(TraceItemFinishCommand command) {
        if (command == null || command.getTraceItemId() == null) {
            return;
        }
        try {
            TraceItem item = new TraceItem();
            item.setId(command.getTraceItemId());
            item.setOutputPayload(command.getOutputPayload());
            item.setOutputSummary(command.getOutputSummary());
            item.setErrorMessage(command.getErrorMessage());
            item.setEndTimestamp(command.getEndTimestamp());
            item.setStatus(command.getStatus());
            if (command.getTokenUsage() != null) {
                item.setPromptTokens(command.getTokenUsage().promptTokens());
                item.setCompletionTokens(command.getTokenUsage().completionTokens());
                item.setTotalTokens(command.getTokenUsage().totalTokens());
            }
            traceItemMapper.updateById(item);
        } catch (Exception e) {
            log.error("Failed to update trace item, traceItemId={}", command.getTraceItemId(), e);
        }
    }
}
