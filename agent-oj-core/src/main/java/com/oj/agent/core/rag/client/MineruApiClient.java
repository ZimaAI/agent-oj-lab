package com.oj.agent.core.rag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.rag.config.MineruProperties;
import com.oj.agent.core.rag.exception.KnowledgeDocumentException;
import com.oj.agent.core.rag.model.dto.MineruTaskResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class MineruApiClient {

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();

    private static final String STATE_DONE = "done";
    private static final String STATE_FAIL = "failed";

    private final HttpClient httpClient;
    private final MineruProperties properties;

    public MineruApiClient(@Qualifier("knowledgeHttpClient") HttpClient knowledgeHttpClient, MineruProperties properties) {
        this.httpClient = knowledgeHttpClient;
        this.properties = properties;
    }

    public String submitUrlTask(String fileUrl, String fileName) {
        ensureApiKey();
        try {
            JsonNode payload = OBJECT_MAPPER.createObjectNode()
                    .put("url", fileUrl)
                    .put("file_name", defaultFileName(fileName))
                    .put("language", properties.getLanguage())
                    .put("enable_formula", properties.isEnableFormula());
            JsonNode body = postJson("/api/v4/extract/task", payload);
            String taskId = readPath(body, "data", "task_id");
            if (!StringUtils.hasText(taskId)) {
                throw new KnowledgeDocumentException("Mineru submit url task failed: task_id missing");
            }
            return taskId.trim();
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Mineru submit url task failed", e);
        }
    }

    public String submitUrlBatch(List<UrlBatchFile> files) {
        ensureApiKey();
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }
        try {
            var payload = OBJECT_MAPPER.createObjectNode()
                    .put("language", properties.getLanguage())
                    .put("enable_formula", properties.isEnableFormula());
            var arr = payload.putArray("files");
            for (UrlBatchFile file : files) {
                var item = OBJECT_MAPPER.createObjectNode()
                        .put("url", file.url())
                        .put("data_id", file.dataId());
                arr.add(item);
            }
            JsonNode body = postJson("/api/v4/extract/task/batch", payload);
            String batchId = readPath(body, "data", "batch_id");
            if (!StringUtils.hasText(batchId)) {
                throw new KnowledgeDocumentException("Mineru submit url batch failed: batch_id missing");
            }
            return batchId.trim();
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Mineru submit url batch failed", e);
        }
    }

    public String submitLocalBatch(List<Path> files) {
        ensureApiKey();
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }
        try {
            var payload = OBJECT_MAPPER.createObjectNode();
            var arr = payload.putArray("files");
            for (Path file : files) {
                arr.add(OBJECT_MAPPER.createObjectNode()
                        .put("name", file.getFileName().toString()));
            }
            JsonNode body = postJson("/api/v4/file-urls/batch", payload);
            String batchId = readPath(body, "data", "batch_id");
            JsonNode uploadUrls = readPathNode(body, "data", "file_urls");
            if (!StringUtils.hasText(batchId) || uploadUrls == null || !uploadUrls.isArray()) {
                throw new KnowledgeDocumentException("Mineru submit local batch failed: invalid upload urls response");
            }
            if (uploadUrls.size() != files.size()) {
                throw new KnowledgeDocumentException("Mineru submit local batch failed: upload url count mismatch");
            }
            for (int i = 0; i < files.size(); i++) {
                uploadLocalFile(files.get(i), uploadUrls.get(i).asText());
            }
            return batchId.trim();
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Mineru submit local batch failed", e);
        }
    }

    public MineruTaskResult waitTaskResult(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        int attempts = Math.max(properties.getMaxPollAttempts(), 1);
        for (int i = 0; i < attempts; i++) {
            MineruTaskResult result = queryTask(taskId);
            if (isDone(result.getState()) || isFailed(result.getState())) {
                return result;
            }
            sleepPollingInterval();
        }
        throw new KnowledgeDocumentException("Mineru task polling timeout: " + taskId);
    }

    public List<MineruTaskResult> waitBatchResults(String batchId) {
        if (!StringUtils.hasText(batchId)) {
            throw new IllegalArgumentException("batchId must not be blank");
        }
        int attempts = Math.max(properties.getMaxPollAttempts(), 1);
        for (int i = 0; i < attempts; i++) {
            List<MineruTaskResult> results = queryBatch(batchId);
            if (!results.isEmpty() && results.stream().allMatch(result -> isDone(result.getState()) || isFailed(result.getState()))) {
                return results;
            }
            sleepPollingInterval();
        }
        throw new KnowledgeDocumentException("Mineru batch polling timeout: " + batchId);
    }

    public Path downloadZip(String zipUrl, Path targetZipPath) {
        if (!StringUtils.hasText(zipUrl)) {
            throw new IllegalArgumentException("zipUrl must not be blank");
        }
        if (targetZipPath == null) {
            throw new IllegalArgumentException("targetZipPath must not be null");
        }
        try {
            Files.createDirectories(targetZipPath.toAbsolutePath().getParent());
            HttpRequest request = HttpRequest.newBuilder(URI.create(zipUrl.trim()))
                    .GET()
                    .timeout(Duration.ofSeconds(120))
                    .build();
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetZipPath));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KnowledgeDocumentException("Mineru zip download failed, status=" + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KnowledgeDocumentException("Mineru zip download failed", e);
        }
    }

    public String newDataId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private MineruTaskResult queryTask(String taskId) {
        try {
            JsonNode body = getJson("/api/v4/extract/task/" + urlEncode(taskId));
            JsonNode data = readPathNode(body, "data");
            JsonNode extractResult = readPathNode(data, "extract_result");
            return MineruTaskResult.builder()
                    .state(readPath(data, "state"))
                    .fullZipUrl(readPath(extractResult, "full_zip_url"))
                    .errMsg(readPath(data, "err_msg"))
                    .taskId(taskId)
                    .build();
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Mineru query task failed", e);
        }
    }

    private List<MineruTaskResult> queryBatch(String batchId) {
        try {
            JsonNode body = getJson("/api/v4/extract-results/batch/" + urlEncode(batchId));
            JsonNode data = readPathNode(body, "data");
            JsonNode extractResult = readPathNode(data, "extract_result");
            List<MineruTaskResult> results = new ArrayList<>();
            if (extractResult != null && extractResult.isArray()) {
                for (JsonNode item : extractResult) {
                    results.add(MineruTaskResult.builder()
                            .state(readPath(item, "state"))
                            .fullZipUrl(readPath(item, "full_zip_url"))
                            .errMsg(readPath(item, "err_msg"))
                            .batchId(batchId)
                            .taskId(readPath(item, "task_id"))
                            .dataId(readPath(item, "data_id"))
                            .build());
                }
            }
            return results;
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Mineru query batch failed", e);
        }
    }

    private void uploadLocalFile(Path filePath, String signedUrl) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl))
                .PUT(HttpRequest.BodyPublishers.ofFile(filePath))
                .timeout(Duration.ofSeconds(120))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KnowledgeDocumentException(
                        "Mineru local upload failed, status=" + response.statusCode() + ", body=" + defaultText(response.body())
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeDocumentException("Mineru local upload interrupted", e);
        }
    }

    private JsonNode postJson(String path, JsonNode payload) throws IOException {
        try {
            String bodyString = OBJECT_MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponseBody(path, response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeDocumentException("Mineru request interrupted: " + path, e);
        }
    }

    private JsonNode getJson(String path) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                    .GET()
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponseBody(path, response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeDocumentException("Mineru request interrupted: " + path, e);
        }
    }

    private JsonNode parseResponseBody(String path, int statusCode, String responseBody) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new KnowledgeDocumentException("Mineru request failed, path=" + path + ", status=" + statusCode + ", body=" + responseBody);
        }
        if (!StringUtils.hasText(responseBody)) {
            throw new KnowledgeDocumentException("Mineru empty response body, path=" + path);
        }
        JsonNode body = OBJECT_MAPPER.readTree(responseBody);
        int code = body.path("code").asInt(-1);
        if (code != 0) {
            throw new KnowledgeDocumentException("Mineru business failed, path=" + path + ", code=" + code + ", msg=" + body.path("msg").asText());
        }
        return body;
    }

    private URI buildUri(String path) {
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Mineru baseUrl must not be blank");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + path);
    }

    private void ensureApiKey() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new KnowledgeDocumentException("Mineru apiKey is not configured, please set app.mineru.api-key");
        }
    }

    private void sleepPollingInterval() {
        long intervalMillis = Math.max(properties.getPollIntervalMillis(), 500L);
        try {
            Thread.sleep(intervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeDocumentException("Mineru polling interrupted", e);
        }
    }

    private boolean isDone(String state) {
        return STATE_DONE.equalsIgnoreCase(defaultText(state));
    }

    private boolean isFailed(String state) {
        return STATE_FAIL.equalsIgnoreCase(defaultText(state));
    }

    private String defaultFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "document.pdf";
        }
        return fileName.trim();
    }

    private String readPath(JsonNode node, String... keys) {
        JsonNode pathNode = readPathNode(node, keys);
        if (pathNode == null || pathNode.isMissingNode() || pathNode.isNull()) {
            return null;
        }
        String value = pathNode.asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private JsonNode readPathNode(JsonNode node, String... keys) {
        JsonNode current = node;
        if (current == null) {
            return null;
        }
        for (String key : keys) {
            current = current.path(key);
        }
        return current;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String defaultText(String value) {
        return value == null ? "" : value.trim();
    }

    public record UrlBatchFile(String dataId, String url) {
    }
}
