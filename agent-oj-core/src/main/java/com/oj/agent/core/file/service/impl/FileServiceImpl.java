package com.oj.agent.core.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.file.config.MinioFileProperties;
import com.oj.agent.core.file.converter.FileRecordConverter;
import com.oj.agent.core.file.exception.FileStorageException;
import com.oj.agent.core.file.mapper.FileRecordMapper;
import com.oj.agent.core.file.model.command.FileUploadCommand;
import com.oj.agent.core.file.model.entity.FileRecord;
import com.oj.agent.core.file.model.query.FileGetQuery;
import com.oj.agent.core.file.model.query.FilePageQuery;
import com.oj.agent.core.file.model.result.FileRecordResult;
import com.oj.agent.core.file.service.FileService;
import com.oj.agent.security.util.UserContext;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String DEFAULT_FILENAME = "file.bin";
    private static final DateTimeFormatter OBJECT_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileRecordMapper fileRecordMapper;
    private final MinioClient minioClient;
    private final MinioFileProperties properties;

    private volatile boolean bucketChecked = false;

    public FileServiceImpl(FileRecordMapper fileRecordMapper,
                           MinioClient minioClient,
                           MinioFileProperties properties) {
        this.fileRecordMapper = fileRecordMapper;
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileRecordResult upload(FileUploadCommand command) {
        MultipartFile file = command == null ? null : command.getFile();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        Long userId = requireCurrentUserId();
        ensureBucketExists();

        String filename = normalizeFilename(file.getOriginalFilename());
        String objectName = buildObjectName(filename);
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType().trim() : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket().trim())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new FileStorageException("Failed to upload file to MinIO", e);
        }

        FileRecord entity = new FileRecord();
        entity.setUserId(userId);
        entity.setBucketName(properties.getBucket().trim());
        entity.setObjectName(objectName);
        entity.setOriginalFilename(filename);
        entity.setFileSize(file.getSize());
        entity.setContentType(contentType);
        entity.setFileUrl(buildFileUrl(properties.getBucket().trim(), objectName));
        entity.setIsDelete(0);
        fileRecordMapper.insert(entity);

        return FileRecordConverter.toResult(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileRecordResult> pageCurrentUserFiles(FilePageQuery query) {
        Long userId = requireCurrentUserId();
        FilePageQuery safeQuery = query == null ? new FilePageQuery() : query;
        long safePageNum = safeQuery.getPageNum() <= 0 ? DEFAULT_PAGE_NUM : safeQuery.getPageNum();
        long safePageSize = safeQuery.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(safeQuery.getPageSize(), MAX_PAGE_SIZE);

        Page<FileRecord> page = new Page<>(safePageNum, safePageSize);
        LambdaQueryWrapper<FileRecord> queryWrapper = new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId)
                .eq(FileRecord::getIsDelete, 0)
                .orderByDesc(FileRecord::getId);
        Page<FileRecord> entityPage = fileRecordMapper.selectPage(page, queryWrapper);

        Page<FileRecordResult> result = new Page<>(safePageNum, safePageSize, entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream()
                .map(FileRecordConverter::toResult)
                .toList());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public FileRecordResult getCurrentUserFile(FileGetQuery query) {
        Long userId = requireCurrentUserId();
        Long id = query == null ? null : query.getId();
        FileRecord entity = requireOwnedFile(id, userId);
        return FileRecordConverter.toResult(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(FileGetQuery query) {
        Long userId = requireCurrentUserId();
        Long id = query == null ? null : query.getId();
        FileRecord entity = requireOwnedFile(id, userId);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(entity.getBucketName())
                            .object(entity.getObjectName())
                            .build()
            );
        } catch (Exception e) {
            throw new FileStorageException("Failed to delete file from MinIO", e);
        }
        entity.setIsDelete(1);
        fileRecordMapper.updateById(entity);
    }

    private FileRecord requireOwnedFile(Long id, Long userId) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("file id must be a positive number");
        }
        FileRecord entity = fileRecordMapper.selectById(id);
        if (entity == null || entity.getIsDelete() == null || entity.getIsDelete() == 1) {
            throw new IllegalArgumentException("file not found: " + id);
        }
        if (!userId.equals(entity.getUserId())) {
            throw new IllegalArgumentException("no permission to access file: " + id);
        }
        return entity;
    }

    private String buildObjectName(String filename) {
        String extension = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            extension = filename.substring(dot);
        }
        return OBJECT_DATE.format(LocalDate.now()) + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private String buildFileUrl(String bucket, String objectName) {
        String baseUrl = resolveBaseUrl();
        return baseUrl + "/" + bucket + "/" + objectName;
    }

    private String resolveBaseUrl() {
        String baseUrl = StringUtils.hasText(properties.getBaseUrl())
                ? properties.getBaseUrl().trim()
                : properties.getEndpoint().trim();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String normalizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return DEFAULT_FILENAME;
        }
        String normalized = filename.trim();
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
            normalized = normalized.substring(slashIndex + 1);
        }
        return normalized.isBlank() ? DEFAULT_FILENAME : normalized;
    }

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("Current user login required");
        }
        return userId;
    }

    private void ensureBucketExists() {
        if (bucketChecked) {
            return;
        }
        synchronized (this) {
            if (bucketChecked) {
                return;
            }
            try {
                String bucket = properties.getBucket().trim();
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    if (Boolean.TRUE.equals(properties.getAutoCreateBucket())) {
                        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    } else {
                        throw new FileStorageException("MinIO bucket does not exist: " + bucket);
                    }
                }
                bucketChecked = true;
            } catch (FileStorageException e) {
                throw e;
            } catch (Exception e) {
                throw new FileStorageException("Failed to verify MinIO bucket", e);
            }
        }
    }
}
