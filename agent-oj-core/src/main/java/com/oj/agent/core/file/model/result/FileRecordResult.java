package com.oj.agent.core.file.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileRecordResult {

    private Long id;

    private String originalFilename;

    private Long fileSize;

    private String contentType;

    private String fileUrl;

    private String bucketName;

    private String objectName;

    private LocalDateTime createTime;
}
