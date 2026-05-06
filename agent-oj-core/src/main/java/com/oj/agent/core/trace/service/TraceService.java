package com.oj.agent.core.trace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.trace.model.query.TraceDetailQuery;
import com.oj.agent.core.trace.model.query.TracePageQuery;
import com.oj.agent.core.trace.model.result.TraceDetailResult;
import com.oj.agent.core.trace.model.result.TraceListItemResult;

public interface TraceService {

    /**
     * 分页查询链路列表。
     */
    Page<TraceListItemResult> pageTraces(TracePageQuery query);

    /**
     * 查询链路详情。
     */
    TraceDetailResult getTraceDetail(TraceDetailQuery query);
}
