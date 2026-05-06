package com.oj.agent.core.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RAGProperties {
    private Similarity similarity = new Similarity();
    private Integer topK = 5;
    private Double preferenceWeight = 0.3;

    @Data
    public static class Similarity {
        private Double hitThreshold = 0.9;
        private Double referenceThreshold = 0.7;
    }
}
