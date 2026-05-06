package com.oj.agent.core.rag.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public interface KnowledgeDocumentAsyncService {

    void processUploadedDocumentAsync(Long docId,
                                      Path downloadedPath,
                                      UploadedFileType uploadedFileType,
                                      Map<String, Object> traceMeta);

    void processUploadedDocumentCompensation(Long docId, Map<String, Object> traceMeta);

    enum UploadedFileType {
        PDF(".pdf"),
        MARKDOWN(".md"),
        ZIP(".zip");

        private final String downloadSuffix;

        UploadedFileType(String downloadSuffix) {
            this.downloadSuffix = downloadSuffix;
        }

        public String getDownloadSuffix() {
            return downloadSuffix;
        }

        public static UploadedFileType resolveByFilename(String fileName) {
            if (!StringUtils.hasText(fileName)) {
                throw new IllegalArgumentException("fileName must not be blank");
            }
            String lower = fileName.trim().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf")) {
                return PDF;
            }
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
                return MARKDOWN;
            }
            if (lower.endsWith(".zip")) {
                return ZIP;
            }
            throw new IllegalArgumentException("Unsupported upload file type: " + fileName);
        }

        public static UploadedFileType resolveByPersistedValue(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (UploadedFileType type : values()) {
                if (type.name().equals(normalized)) {
                    return type;
                }
            }
            return null;
        }
    }
}
