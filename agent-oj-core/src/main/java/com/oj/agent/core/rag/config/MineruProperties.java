package com.oj.agent.core.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.mineru")
public class MineruProperties {

    private String baseUrl = "https://mineru.net";

    private String apiKey;

    private long pollIntervalMillis = 3000L;

    private int maxPollAttempts = 120;

    private boolean enableFormula = true;

    private String language = "ch";
}
