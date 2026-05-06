package com.oj.agent.core.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.file.minio")
public class MinioFileProperties {

    private String endpoint = "http://localhost:9000";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    private String bucket = "agent-oj-files";

    private String baseUrl = "http://localhost:9000";

    private Boolean autoCreateBucket = true;
}
