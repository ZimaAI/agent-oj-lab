package com.oj.agent.security.endpoint.service.impl;

import com.oj.agent.security.annotation.RequirePermission;
import com.oj.agent.security.endpoint.model.query.EndpointScanQuery;
import com.oj.agent.security.endpoint.model.result.EndpointResult;
import com.oj.agent.security.endpoint.service.EndpointService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EndpointServiceImpl implements EndpointService {

    private final RequestMappingHandlerMapping handlerMapping;

    public EndpointServiceImpl(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public List<EndpointResult> scanEndpoints(EndpointScanQuery query) {
        List<EndpointResult> endpoints = new ArrayList<>();
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            Set<String> patterns = mappingInfo.getPatternValues();
            if (patterns.isEmpty()) {
                continue;
            }

            Set<String> methods = mappingInfo.getMethodsCondition().getMethods()
                    .stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            String controllerName = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();
            String boundPermission = getBoundPermission(handlerMethod);

            for (String pattern : patterns) {
                if (methods.isEmpty()) {
                    endpoints.add(new EndpointResult(pattern, "ALL", controllerName, methodName, boundPermission));
                    continue;
                }
                for (String method : methods) {
                    endpoints.add(new EndpointResult(pattern, method, controllerName, methodName, boundPermission));
                }
            }
        }

        return endpoints;
    }

    private String getBoundPermission(HandlerMethod handlerMethod) {
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null || annotation.value().length == 0) {
            return null;
        }
        return annotation.value()[0];
    }
}
