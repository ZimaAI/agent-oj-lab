package com.oj.agent.security.endpoint.controller;

import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import com.oj.agent.security.endpoint.converter.EndpointConverter;
import com.oj.agent.security.endpoint.model.response.EndpointResponse;
import com.oj.agent.security.endpoint.service.EndpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointService endpointService;

    @GetMapping("/scan")
    @RequirePermission("permission:create")
    public Result<List<EndpointResponse>> scanEndpoints() {
        return Result.success(EndpointConverter.toResponses(
                endpointService.scanEndpoints(EndpointConverter.toScanQuery())
        ));
    }
}
