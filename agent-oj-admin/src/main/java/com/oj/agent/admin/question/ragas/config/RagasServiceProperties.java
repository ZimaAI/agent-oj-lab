package com.oj.agent.admin.question.ragas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ragas.service")
public class RagasServiceProperties {

    private String baseUrl = "http://127.0.0.1:8000";

    private Integer connectTimeoutMillis = 3000;

    private Integer readTimeoutMillis = 120000;
}
