package com.oj.agent.security.endpoint.converter;

import com.oj.agent.security.endpoint.model.query.EndpointScanQuery;
import com.oj.agent.security.endpoint.model.response.EndpointResponse;
import com.oj.agent.security.endpoint.model.result.EndpointResult;

import java.util.Collections;
import java.util.List;

public final class EndpointConverter {

    private EndpointConverter() {
    }

    public static EndpointScanQuery toScanQuery() {
        return new EndpointScanQuery();
    }

    public static EndpointResponse toResponse(EndpointResult result) {
        if (result == null) {
            return null;
        }
        return new EndpointResponse(
                result.getUrl(),
                result.getHttpMethod(),
                result.getControllerName(),
                result.getMethodName(),
                result.getBoundPermission()
        );
    }

    public static List<EndpointResponse> toResponses(List<EndpointResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(EndpointConverter::toResponse)
                .toList();
    }
}
