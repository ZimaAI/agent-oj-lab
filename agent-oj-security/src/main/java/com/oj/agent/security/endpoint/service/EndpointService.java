package com.oj.agent.security.endpoint.service;

import com.oj.agent.security.endpoint.model.query.EndpointScanQuery;
import com.oj.agent.security.endpoint.model.result.EndpointResult;

import java.util.List;

public interface EndpointService {

    List<EndpointResult> scanEndpoints(EndpointScanQuery query);
}
