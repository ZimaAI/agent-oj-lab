package com.oj.agent.core.file.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileRecordResponse {

    private Long id;

    private String originalFilename;

    private Long fileSize;

    private String contentType;

    private String fileUrl;

    private String bucketName;

    private String objectName;

    private LocalDateTime createTime;
}
