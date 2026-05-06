package com.oj.agent.core.trace.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.trace.converter.TraceConverter;
import com.oj.agent.core.trace.mapper.TraceItemMapper;
import com.oj.agent.core.trace.model.entity.TraceItem;
import com.oj.agent.core.trace.model.query.TraceDetailQuery;
import com.oj.agent.core.trace.model.query.TraceItemListQuery;
import com.oj.agent.core.trace.model.result.TraceItemResult;
import com.oj.agent.core.trace.service.TraceItemService;
import com.oj.agent.core.trace.service.TraceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TraceItemServiceImpl extends ServiceImpl<TraceItemMapper, TraceItem>
        implements TraceItemService {

    // 链路服务。
    private final TraceService traceService;

    public TraceItemServiceImpl(TraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 查询链路节点记录列表。
     */
    @Override
    public List<TraceItemResult> listTraceItems(TraceItemListQuery query) {
        String traceId = query == null ? null : query.getTraceId();
        if (!StringUtils.hasText(traceId)) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
        // 先校验链路属于当前用户，再查询对应节点明细。
        traceService.getTraceDetail(new TraceDetailQuery(traceId));
        return baseMapper.selectByTraceId(traceId).stream()
                .map(TraceConverter::toItemResult)
                .toList();
    }

}
