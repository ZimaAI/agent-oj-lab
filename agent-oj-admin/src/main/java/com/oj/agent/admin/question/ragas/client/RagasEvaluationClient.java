package com.oj.agent.admin.question.ragas.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.admin.question.ragas.config.RagasServiceProperties;
import com.oj.agent.core.rag.model.dto.RagasEvaluateResponseDTO;
import com.oj.agent.core.rag.model.dto.RagasEvaluateSampleDTO;
import com.oj.agent.common.util.JsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class RagasEvaluationClient {

    private static final int MAX_ERROR_BODY_LENGTH = 2000;

    private final RagasServiceProperties ragasServiceProperties;

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public RagasEvaluationClient(RagasServiceProperties ragasServiceProperties) {
        this.ragasServiceProperties = ragasServiceProperties;
    }

    public RagasEvaluateResponseDTO evaluate(List<RagasEvaluateSampleDTO> samples) {
        if (samples == null || samples.isEmpty()) {
            RagasEvaluateResponseDTO emptyResponse = new RagasEvaluateResponseDTO();
            emptyResponse.setDegraded(false);
            emptyResponse.setResults(List.of());
            return emptyResponse;
        }
        if (!StringUtils.hasText(ragasServiceProperties.getBaseUrl())) {
            throw new IllegalStateException("ragas service baseUrl is blank");
        }

        try {
            String targetUrl = ragasServiceProperties.getBaseUrl().replaceAll("/+$", "") + "/evaluate";
            String requestBody = objectMapper.writeValueAsString(Map.of("samples", samples));
            // 强制使用 HTTP/1.1，避免 uvicorn 对 h2c upgrade 的兼容问题导致 422。
            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofMillis(Math.max(100, ragasServiceProperties.getConnectTimeoutMillis())))
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(Math.max(1000, ragasServiceProperties.getReadTimeoutMillis())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("call ragas service failed with status " + response.statusCode()
                        + ", body=" + truncateResponseBody(response.body()));
            }

            Map<String, Object> responseMap = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            return objectMapper.convertValue(responseMap, RagasEvaluateResponseDTO.class);
        } catch (Exception exception) {
            throw new IllegalStateException("call ragas service failed: " + exception.getMessage(), exception);
        }
    }

    private String truncateResponseBody(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        if (responseBody.length() <= MAX_ERROR_BODY_LENGTH) {
            return responseBody;
        }
        return responseBody.substring(0, MAX_ERROR_BODY_LENGTH) + "...(truncated)";
    }
}
