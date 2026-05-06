package com.oj.agent.core.trace.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.common.api.Result;
import com.oj.agent.core.trace.converter.TraceConverter;
import com.oj.agent.core.trace.model.request.TracePageRequest;
import com.oj.agent.core.trace.model.response.TraceDetailResponse;
import com.oj.agent.core.trace.model.response.TraceItemResponse;
import com.oj.agent.core.trace.model.response.TraceListItemResponse;
import com.oj.agent.core.trace.service.TraceItemService;
import com.oj.agent.core.trace.service.TraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trace")
public class TraceController {

    // 链路服务。
    private final TraceService traceService;

    // 链路明细服务。
    private final TraceItemService traceItemService;

    public TraceController(TraceService traceService, TraceItemService traceItemService) {
        this.traceService = traceService;
        this.traceItemService = traceItemService;
    }

    // 分页查询链路列表。
    @PostMapping("/page")
    public Result<Page<TraceListItemResponse>> pageTraces(@RequestBody(required = false) TracePageRequest request) {
        return Result.success(TraceConverter.toListItemResponsePage(
                traceService.pageTraces(TraceConverter.toPageQuery(request))
        ));
    }

    // 查询链路详情。
    @GetMapping("/{traceId}")
    public Result<TraceDetailResponse> getTraceDetail(@PathVariable String traceId) {
        return Result.success(TraceConverter.toDetailResponse(
                traceService.getTraceDetail(TraceConverter.toDetailQuery(traceId))
        ));
    }

    // 查询链路节点记录列表。
    @GetMapping("/{traceId}/items")
    public Result<List<TraceItemResponse>> listTraceItems(@PathVariable String traceId) {
        return Result.success(traceItemService.listTraceItems(TraceConverter.toItemListQuery(traceId))
                .stream()
                .map(TraceConverter::toItemResponse)
                .toList());
    }
}
