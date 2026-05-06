package com.oj.agent.core.trace.service;

import com.oj.agent.core.trace.model.query.TraceItemListQuery;
import com.oj.agent.core.trace.model.result.TraceItemResult;

import java.util.List;

public interface TraceItemService {

    /**
     * 查询链路节点记录列表。
     */
    List<TraceItemResult> listTraceItems(TraceItemListQuery query);
}
