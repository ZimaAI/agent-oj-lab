package com.oj.agent.security.endpoint.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndpointResult {

    private String url;

    private String httpMethod;

    private String controllerName;

    private String methodName;

    private String boundPermission;
}
