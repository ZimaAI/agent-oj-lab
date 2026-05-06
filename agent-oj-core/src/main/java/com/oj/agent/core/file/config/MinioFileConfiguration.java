package com.oj.agent.core.file.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MinioFileConfiguration {

    @Bean
    public MinioClient minioClient(MinioFileProperties properties) {
        assertHasText(properties.getEndpoint(), "app.file.minio.endpoint must not be blank");
        assertHasText(properties.getAccessKey(), "app.file.minio.access-key must not be blank");
        assertHasText(properties.getSecretKey(), "app.file.minio.secret-key must not be blank");
        assertHasText(properties.getBucket(), "app.file.minio.bucket must not be blank");
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            properties.setBaseUrl(properties.getEndpoint());
        }
        return MinioClient.builder()
                .endpoint(properties.getEndpoint().trim())
                .credentials(properties.getAccessKey().trim(), properties.getSecretKey().trim())
                .build();
    }

    private void assertHasText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
