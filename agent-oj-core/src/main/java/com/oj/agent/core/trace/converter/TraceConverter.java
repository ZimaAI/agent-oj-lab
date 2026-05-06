package com.oj.agent.core.trace.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.trace.model.entity.Trace;
import com.oj.agent.core.trace.model.entity.TraceItem;
import com.oj.agent.core.trace.model.query.TraceDetailQuery;
import com.oj.agent.core.trace.model.query.TraceItemListQuery;
import com.oj.agent.core.trace.model.query.TracePageQuery;
import com.oj.agent.core.trace.model.request.TracePageRequest;
import com.oj.agent.core.trace.model.response.TraceDetailResponse;
import com.oj.agent.core.trace.model.response.TraceItemResponse;
import com.oj.agent.core.trace.model.response.TraceListItemResponse;
import com.oj.agent.core.trace.model.result.TraceDetailResult;
import com.oj.agent.core.trace.model.result.TraceItemResult;
import com.oj.agent.core.trace.model.result.TraceListItemResult;

public final class TraceConverter {

    private TraceConverter() {
    }

    public static TracePageQuery toPageQuery(TracePageRequest request) {
        TracePageQuery query = new TracePageQuery();
        if (request == null) {
            return query;
        }
        query.setPageNum(request.getPageNum());
        query.setPageSize(request.getPageSize());
        query.setTraceId(request.getTraceId());
        query.setConversationId(request.getConversationId());
        query.setUserId(request.getUserId());
        query.setRequestMessage(request.getRequestMessage());
        query.setStatus(request.getStatus());
        return query;
    }

    public static TraceDetailQuery toDetailQuery(String traceId) {
        return new TraceDetailQuery(traceId);
    }

    public static TraceItemListQuery toItemListQuery(String traceId) {
        return new TraceItemListQuery(traceId);
    }

    public static TraceListItemResult toListItemResult(Trace trace) {
        if (trace == null) {
            return null;
        }
        TraceListItemResult result = new TraceListItemResult();
        result.setId(trace.getId());
        result.setTraceId(trace.getTraceId());
        result.setConversationId(trace.getConversationId());
        result.setUserId(trace.getUserId());
        result.setRequestMessage(trace.getRequestMessage());
        result.setCurrentAlgorithmQuestionId(trace.getCurrentAlgorithmQuestionId());
        result.setStatus(trace.getStatus());
        result.setErrorMessage(trace.getErrorMessage());
        result.setCreateTime(trace.getCreateTime());
        result.setUpdateTime(trace.getUpdateTime());
        return result;
    }

    public static TraceDetailResult toDetailResult(Trace trace) {
        if (trace == null) {
            return null;
        }
        TraceDetailResult result = new TraceDetailResult();
        result.setId(trace.getId());
        result.setTraceId(trace.getTraceId());
        result.setConversationId(trace.getConversationId());
        result.setUserId(trace.getUserId());
        result.setRequestMessage(trace.getRequestMessage());
        result.setCurrentAlgorithmQuestionId(trace.getCurrentAlgorithmQuestionId());
        result.setCurrentAlgorithmQuestionSnapshot(trace.getCurrentAlgorithmQuestionSnapshot());
        result.setStatus(trace.getStatus());
        result.setErrorMessage(trace.getErrorMessage());
        result.setCreateTime(trace.getCreateTime());
        result.setUpdateTime(trace.getUpdateTime());
        return result;
    }

    public static TraceItemResult toItemResult(TraceItem traceItem) {
        if (traceItem == null) {
            return null;
        }
        TraceItemResult result = new TraceItemResult();
        result.setId(traceItem.getId());
        result.setTraceId(traceItem.getTraceId());
        result.setNodeName(traceItem.getNodeName());
        result.setItemType(traceItem.getItemType());
        result.setItemKey(traceItem.getItemKey());
        result.setRoundNo(traceItem.getRoundNo());
        result.setToolName(traceItem.getToolName());
        result.setInputPayload(traceItem.getInputPayload());
        result.setOutputPayload(traceItem.getOutputPayload());
        result.setInputSummary(traceItem.getInputSummary());
        result.setOutputSummary(traceItem.getOutputSummary());
        result.setPromptTokens(traceItem.getPromptTokens());
        result.setCompletionTokens(traceItem.getCompletionTokens());
        result.setTotalTokens(traceItem.getTotalTokens());
        result.setStatus(traceItem.getStatus());
        result.setErrorMessage(traceItem.getErrorMessage());
        result.setStartTimestamp(traceItem.getStartTimestamp());
        result.setEndTimestamp(traceItem.getEndTimestamp());
        result.setCreateTime(traceItem.getCreateTime());
        result.setUpdateTime(traceItem.getUpdateTime());
        return result;
    }

    public static TraceListItemResponse toListItemResponse(TraceListItemResult result) {
        if (result == null) {
            return null;
        }
        TraceListItemResponse response = new TraceListItemResponse();
        response.setId(result.getId());
        response.setTraceId(result.getTraceId());
        response.setConversationId(result.getConversationId());
        response.setUserId(result.getUserId());
        response.setRequestMessage(result.getRequestMessage());
        response.setCurrentAlgorithmQuestionId(result.getCurrentAlgorithmQuestionId());
        response.setStatus(result.getStatus());
        response.setErrorMessage(result.getErrorMessage());
        response.setCreateTime(result.getCreateTime());
        response.setUpdateTime(result.getUpdateTime());
        return response;
    }

    public static TraceDetailResponse toDetailResponse(TraceDetailResult result) {
        if (result == null) {
            return null;
        }
        TraceDetailResponse response = new TraceDetailResponse();
        response.setId(result.getId());
        response.setTraceId(result.getTraceId());
        response.setConversationId(result.getConversationId());
        response.setUserId(result.getUserId());
        response.setRequestMessage(result.getRequestMessage());
        response.setCurrentAlgorithmQuestionId(result.getCurrentAlgorithmQuestionId());
        response.setCurrentAlgorithmQuestionSnapshot(result.getCurrentAlgorithmQuestionSnapshot());
        response.setStatus(result.getStatus());
        response.setErrorMessage(result.getErrorMessage());
        response.setCreateTime(result.getCreateTime());
        response.setUpdateTime(result.getUpdateTime());
        return response;
    }

    public static TraceItemResponse toItemResponse(TraceItemResult result) {
        if (result == null) {
            return null;
        }
        TraceItemResponse response = new TraceItemResponse();
        response.setId(result.getId());
        response.setTraceId(result.getTraceId());
        response.setNodeName(result.getNodeName());
        response.setItemType(result.getItemType());
        response.setItemKey(result.getItemKey());
        response.setRoundNo(result.getRoundNo());
        response.setToolName(result.getToolName());
        response.setInputPayload(result.getInputPayload());
        response.setOutputPayload(result.getOutputPayload());
        response.setInputSummary(result.getInputSummary());
        response.setOutputSummary(result.getOutputSummary());
        response.setPromptTokens(result.getPromptTokens());
        response.setCompletionTokens(result.getCompletionTokens());
        response.setTotalTokens(result.getTotalTokens());
        response.setStatus(result.getStatus());
        response.setErrorMessage(result.getErrorMessage());
        response.setStartTimestamp(result.getStartTimestamp());
        response.setEndTimestamp(result.getEndTimestamp());
        response.setCreateTime(result.getCreateTime());
        response.setUpdateTime(result.getUpdateTime());
        return response;
    }

    public static Page<TraceListItemResponse> toListItemResponsePage(Page<TraceListItemResult> resultPage) {
        Page<TraceListItemResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(TraceConverter::toListItemResponse)
                .toList());
        return responsePage;
    }
}
