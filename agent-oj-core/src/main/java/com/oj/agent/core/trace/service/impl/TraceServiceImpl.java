package com.oj.agent.core.trace.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.trace.converter.TraceConverter;
import com.oj.agent.core.trace.model.entity.Trace;
import com.oj.agent.core.trace.mapper.TraceMapper;
import com.oj.agent.core.trace.model.query.TraceDetailQuery;
import com.oj.agent.core.trace.model.query.TracePageQuery;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oj.agent.core.trace.model.result.TraceDetailResult;
import com.oj.agent.core.trace.model.result.TraceListItemResult;
import com.oj.agent.core.trace.service.TraceService;
import com.oj.agent.security.util.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TraceServiceImpl extends ServiceImpl<TraceMapper, Trace>
        implements TraceService {

    private static final long MAX_PAGE_SIZE = 100;

    /**
     * 分页查询链路列表。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TraceListItemResult> pageTraces(TracePageQuery query) {
        TracePageQuery safeQuery = query == null ? new TracePageQuery() : query;
        validatePageParams(safeQuery.getPageNum(), safeQuery.getPageSize());
        Long currentUserId = getCurrentUserId();
        Page<Trace> page = new Page<>(safeQuery.getPageNum(), safeQuery.getPageSize());
        var tracePage = baseMapper.selectPageByCondition(page,
                safeQuery.getTraceId(),
                safeQuery.getConversationId(),
                currentUserId,
                safeQuery.getRequestMessage(),
                safeQuery.getStatus());
        long total = tracePage.getTotal();
        if (total <= 0) {
            total = baseMapper.countByCondition(
                    safeQuery.getTraceId(),
                    safeQuery.getConversationId(),
                    currentUserId,
                    safeQuery.getRequestMessage(),
                    safeQuery.getStatus()
            );
        }
        Page<TraceListItemResult> result = new Page<>(safeQuery.getPageNum(), safeQuery.getPageSize(), total);
        result.setRecords(tracePage.getRecords().stream()
                .map(TraceConverter::toListItemResult)
                .toList());
        return result;
    }

    /**
     * 查询链路详情。
     */
    @Override
    @Transactional(readOnly = true)
    public TraceDetailResult getTraceDetail(TraceDetailQuery query) {
        String traceId = query == null ? null : query.getTraceId();
        validateTraceId(traceId);
        Long currentUserId = getCurrentUserId();
        Trace trace = lambdaQuery()
                .eq(Trace::getTraceId, traceId)
                .eq(Trace::getUserId, currentUserId)
                .one();
        if (trace == null) {
            throw new IllegalArgumentException("trace 不存在");
        }
        return TraceConverter.toDetailResult(trace);
    }

    // 获取当前登录用户 id。
    private Long getCurrentUserId() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("当前用户未登录");
        }
        return currentUserId;
    }

    // 校验分页参数为合法正整数且分页大小不超过上限。
    private void validatePageParams(long current, long pageSize) {
        if (current <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("分页参数非法");
        }
    }

    // 校验链路 id。
    private void validateTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
    }

}
