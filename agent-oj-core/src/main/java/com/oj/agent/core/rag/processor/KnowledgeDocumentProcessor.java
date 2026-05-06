package com.oj.agent.core.rag.processor;

import com.oj.agent.core.rag.model.dto.ConvertedDocumentResult;
import com.oj.agent.core.rag.client.MineruApiClient;
import com.oj.agent.core.rag.util.KnowledgeMarkdownImageRewriter;
import com.oj.agent.core.file.config.MinioFileProperties;
import com.oj.agent.core.file.exception.FileStorageException;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.rag.exception.KnowledgeDocumentException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class KnowledgeDocumentProcessor {

    private static final String IMAGE_DESCRIPTION_PROMPT =
            "用中文为这张图片写一句简洁的描述，用于 Markdown 格式的替代文本（alt text）。仅返回描述文本。";
    private static final Pattern ABSOLUTE_URL_PATTERN = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final Set<String> IMAGE_SUFFIXES = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg");
    private static final Set<String> MARKDOWN_SUFFIXES = Set.of(".md", ".markdown");
    private static final String KEY_MD_URLS = "mdUrls";
    private static final String KEY_IMAGE_URLS = "imageUrls";
    private static final String KEY_EMBEDDINGS = "embeddings";
    private static final String KEY_MINERU = "mineru";
    private static final DateTimeFormatter OBJECT_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MineruApiClient mineruApiClient;
    private final MinioClient minioClient;
    private final MinioFileProperties minioProperties;
    private final HttpClient knowledgeHttpClient;
    private final Executor knowledgeCleanupExecutor;
    private final ChatClient chatClient;

    private volatile boolean bucketChecked = false;

    public KnowledgeDocumentProcessor(MineruApiClient mineruApiClient,
                                      MinioClient minioClient,
                                      MinioFileProperties minioProperties,
                                      @Qualifier("knowledgeHttpClient") HttpClient knowledgeHttpClient,
                                      @Qualifier("knowledgeCleanupExecutor") Executor knowledgeCleanupExecutor,
                                      AiModelRegistry aiModelRegistry) {
        this.mineruApiClient = mineruApiClient;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.knowledgeHttpClient = knowledgeHttpClient;
        this.knowledgeCleanupExecutor = knowledgeCleanupExecutor;
        this.chatClient = aiModelRegistry.getDefaultChatClient();
    }

    public ConvertedDocumentResult convertZipAndVectorize(Long documentId,
                                                          String docTitle,
                                                          String zipUrl,
                                                          Map<String, Object> traceMeta) {
        Path workDir = null;
        try {
            // 下载 MinerU 压缩结果并解压到临时目录。
            workDir = Files.createTempDirectory("knowledge-doc-" + documentId + "-");
            Path zipPath = workDir.resolve("mineru_result.zip");
            mineruApiClient.downloadZip(zipUrl, zipPath);

            Path unzipDir = workDir.resolve("unzipped");
            unzip(zipPath, unzipDir, false);

            // 统一执行 markdown 图片重写和上传。
            Map<String, Object> mineruTraceMeta = mergeMap(
                    new HashMap<>(traceMeta == null ? Map.of() : traceMeta),
                    Map.of("resultZipUrl", zipUrl)
            );
            return convertMarkdownWorkspace(unzipDir, mineruTraceMeta, false);
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Convert zip and vectorize failed", e);
        } finally {
            cleanupDirectory(workDir);
        }
    }

    public ConvertedDocumentResult convertUploadedMarkdownAndVectorize(Long documentId,
                                                                       String docTitle,
                                                                       Path markdownPath,
                                                                       Map<String, Object> traceMeta) {
        if (markdownPath == null || !Files.exists(markdownPath)) {
            throw new KnowledgeDocumentException("Uploaded markdown file does not exist");
        }
        if (!isMarkdownFile(markdownPath.getFileName().toString())) {
            throw new KnowledgeDocumentException("Uploaded markdown suffix is invalid");
        }
        Path workDir = null;
        try {
            // 单 markdown 上传场景下，先复制到临时目录统一走重写逻辑。
            workDir = Files.createTempDirectory("knowledge-upload-md-" + documentId + "-");
            Path sourceDir = workDir.resolve("source");
            Files.createDirectories(sourceDir);
            String fileName = StringUtils.hasText(markdownPath.getFileName().toString())
                    ? markdownPath.getFileName().toString()
                    : "document.md";
            Path copied = sourceDir.resolve(fileName);
            Files.copy(markdownPath, copied, StandardCopyOption.REPLACE_EXISTING);

            Map<String, Object> uploadTraceMeta = new HashMap<>();
            if (traceMeta != null) {
                uploadTraceMeta.putAll(traceMeta);
            }
            uploadTraceMeta.putIfAbsent("uploadType", "MARKDOWN");
            uploadTraceMeta.putIfAbsent("sourceFileName", fileName);
            return convertMarkdownWorkspace(sourceDir, uploadTraceMeta, false);
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Convert uploaded markdown failed", e);
        } finally {
            cleanupDirectory(workDir);
        }
    }

    public ConvertedDocumentResult convertUploadedZipAndVectorize(Long documentId,
                                                                  String docTitle,
                                                                  Path zipPath,
                                                                  Map<String, Object> traceMeta) {
        if (zipPath == null || !Files.exists(zipPath)) {
            throw new KnowledgeDocumentException("Uploaded zip file does not exist");
        }
        Path workDir = null;
        try {
            // 上传 zip 必须做内容白名单校验，仅允许 markdown 和图片。
            workDir = Files.createTempDirectory("knowledge-upload-zip-" + documentId + "-");
            Path unzipDir = workDir.resolve("unzipped");
            unzip(zipPath, unzipDir, true);

            Map<String, Object> uploadTraceMeta = new HashMap<>();
            if (traceMeta != null) {
                uploadTraceMeta.putAll(traceMeta);
            }
            uploadTraceMeta.putIfAbsent("uploadType", "ZIP");
            return convertMarkdownWorkspace(unzipDir, uploadTraceMeta, true);
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Convert uploaded zip failed", e);
        } finally {
            cleanupDirectory(workDir);
        }
    }

    public Path downloadRemoteFile(String fileUrl, String suffix) {
        try {
            Path tempFile = Files.createTempFile("knowledge-source-", suffix);
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl))
                    .GET()
                    .build();
            HttpResponse<Path> response = knowledgeHttpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KnowledgeDocumentException("Download source file failed, status=" + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KnowledgeDocumentException("Download source file failed", e);
        }
    }

    public void cleanupPathAsync(Path path) {
        if (path == null) {
            return;
        }
        knowledgeCleanupExecutor.execute(() -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        });
    }

    private ConvertedDocumentResult convertMarkdownWorkspace(Path workspaceRoot,
                                                             Map<String, Object> traceMeta,
                                                             boolean strictZipMode) throws IOException {
        List<Path> markdownFiles = listMarkdownFiles(workspaceRoot);
        if (markdownFiles.isEmpty()) {
            throw new KnowledgeDocumentException("No markdown files found in uploaded content");
        }

        // 先上传压缩包内（或同目录）的本地图片，便于相对路径直接映射。
        Map<String, String> imageUrlIndex = uploadAllLocalImages(workspaceRoot);
        Map<String, String> remoteImageCache = new LinkedHashMap<>();
        List<String> mdUrls = new ArrayList<>();

        for (Path markdownFile : markdownFiles) {
            String markdown = Files.readString(markdownFile, StandardCharsets.UTF_8);
            String rewritten = rewriteMarkdown(markdown, markdownFile, workspaceRoot, imageUrlIndex, remoteImageCache);
            String markdownUrl = uploadContentToMinio(
                    "knowledge/markdown/",
                    markdownFile.getFileName().toString(),
                    "text/markdown",
                    rewritten.getBytes(StandardCharsets.UTF_8)
            );
            mdUrls.add(markdownUrl);
        }

        Map<String, String> mergedImageIndex = new LinkedHashMap<>();
        mergedImageIndex.putAll(imageUrlIndex);
        mergedImageIndex.putAll(remoteImageCache);

        Map<String, Object> extension = new HashMap<>();
        extension.put(KEY_MD_URLS, mdUrls);
        extension.put(KEY_IMAGE_URLS, mergedImageIndex);
        extension.put(KEY_EMBEDDINGS, new HashMap<String, Object>());
        extension.put(KEY_MINERU, traceMeta == null ? new HashMap<>() : new HashMap<>(traceMeta));
        extension.put("strictZipMode", strictZipMode);

        return ConvertedDocumentResult.builder()
                .convertedDocUrl(mdUrls.get(0))
                .extension(extension)
                .build();
    }

    private String rewriteMarkdown(String markdown,
                                   Path markdownFile,
                                   Path workspaceRoot,
                                   Map<String, String> imageUrlIndex,
                                   Map<String, String> remoteImageCache) {
        Path markdownParent = markdownFile.getParent();
        return KnowledgeMarkdownImageRewriter.rewrite(markdown, originalPath -> {
            String imageUrl = resolveUploadedImageUrl(markdownParent, workspaceRoot, originalPath, imageUrlIndex, remoteImageCache);
            if (!StringUtils.hasText(imageUrl)) {
                return null;
            }
            String description = describeImage(imageUrl, detectMimeTypeByPath(originalPath));
            return new KnowledgeMarkdownImageRewriter.RewrittenImage(description, imageUrl);
        });
    }

    private Map<String, String> uploadAllLocalImages(Path rootDir) throws IOException {
        Map<String, String> imageIndex = new LinkedHashMap<>();
        try (var paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isImageFile)
                    .forEach(path -> {
                        try {
                            String contentType = Files.probeContentType(path);
                            if (!StringUtils.hasText(contentType)) {
                                contentType = detectMimeTypeByPath(path.getFileName().toString());
                            }
                            byte[] bytes = Files.readAllBytes(path);
                            String imageUrl = uploadContentToMinio(
                                    "knowledge/images/",
                                    path.getFileName().toString(),
                                    contentType,
                                    bytes
                            );
                            imageIndex.put(normalizePath(path.toAbsolutePath().toString()), imageUrl);
                        } catch (IOException e) {
                            throw new KnowledgeDocumentException("Upload image to MinIO failed: " + path, e);
                        }
                    });
        }
        return imageIndex;
    }

    private String resolveUploadedImageUrl(Path markdownParent,
                                           Path workspaceRoot,
                                           String originalPath,
                                           Map<String, String> localImageIndex,
                                           Map<String, String> remoteImageCache) {
        if (!StringUtils.hasText(originalPath)) {
            return null;
        }
        String trimmed = originalPath.trim();
        // 外链图片会先下载再转存 MinIO，确保 markdown 中最终地址统一可控。
        if (ABSOLUTE_URL_PATTERN.matcher(trimmed).find()) {
            return remoteImageCache.computeIfAbsent(trimmed, this::downloadAndUploadRemoteImage);
        }
        Path resolved = markdownParent.resolve(trimmed).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            return null;
        }
        return localImageIndex.get(normalizePath(resolved.toAbsolutePath().toString()));
    }

    private String downloadAndUploadRemoteImage(String remoteImageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(remoteImageUrl))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = knowledgeHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KnowledgeDocumentException("Download remote image failed, status=" + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse(detectMimeTypeByPath(remoteImageUrl));
            String fileName = extractFileNameFromUrl(remoteImageUrl);
            return uploadContentToMinio("knowledge/images/", fileName, contentType, response.body());
        } catch (IOException e) {
            throw new KnowledgeDocumentException("Download remote image failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeDocumentException("Download remote image interrupted", e);
        }
    }

    private String describeImage(String imageUrl, String contentType) {
        try {
            MimeType mimeType = StringUtils.hasText(contentType)
                    ? MimeTypeUtils.parseMimeType(contentType)
                    : MimeTypeUtils.IMAGE_JPEG;
            UserMessage userMessage = UserMessage.builder()
                    .text(IMAGE_DESCRIPTION_PROMPT)
                    .media(new Media(mimeType, URI.create(imageUrl)))
                    .build();
            String content = chatClient.prompt().messages(List.of(userMessage)).call().content();
            if (!StringUtils.hasText(content)) {
                return "image";
            }
            String cleaned = content.trim();
            return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
        } catch (Exception e) {
            return "image";
        }
    }

    private List<Path> listMarkdownFiles(Path rootDir) throws IOException {
        List<Path> markdownFiles = new ArrayList<>();
        try (var paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isMarkdownFile(path.getFileName().toString()))
                    .forEach(markdownFiles::add);
        }
        markdownFiles.sort(Comparator.comparing(Path::toString));
        return markdownFiles;
    }

    private void unzip(Path zipPath, Path outputDir, boolean strictContentValidation) throws IOException {
        Files.createDirectories(outputDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = outputDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(outputDir)) {
                    throw new KnowledgeDocumentException("Zip entry path is invalid: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    if (strictContentValidation && !isAllowedZipFile(entry.getName())) {
                        throw new KnowledgeDocumentException("Zip contains unsupported file: " + entry.getName());
                    }
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private boolean isAllowedZipFile(String entryName) {
        if (!StringUtils.hasText(entryName)) {
            return false;
        }
        String normalized = entryName.trim().replace("\\", "/");
        if (normalized.endsWith("/")) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return isMarkdownFile(lower) || isImageFile(lower);
    }

    private boolean isMarkdownFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        for (String suffix : MARKDOWN_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private String uploadContentToMinio(String prefix, String originalName, String contentType, byte[] bytes) {
        ensureBucketExists();
        String safeName = StringUtils.hasText(originalName) ? originalName.trim() : "file.bin";
        String extension = "";
        int dot = safeName.lastIndexOf('.');
        if (dot >= 0) {
            extension = safeName.substring(dot);
        }
        String objectName = prefix
                + OBJECT_DATE.format(LocalDate.now())
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + extension;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket().trim())
                    .object(objectName)
                    .stream(in, bytes.length, -1)
                    .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                    .build());
            return buildFileUrl(minioProperties.getBucket().trim(), objectName);
        } catch (Exception e) {
            throw new FileStorageException("Upload converted content to MinIO failed", e);
        }
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
                String bucket = minioProperties.getBucket().trim();
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    if (Boolean.TRUE.equals(minioProperties.getAutoCreateBucket())) {
                        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    } else {
                        throw new FileStorageException("MinIO bucket does not exist: " + bucket);
                    }
                }
                bucketChecked = true;
            } catch (Exception e) {
                if (e instanceof FileStorageException storageException) {
                    throw storageException;
                }
                throw new FileStorageException("Failed to verify MinIO bucket", e);
            }
        }
    }

    private String buildFileUrl(String bucket, String objectName) {
        String baseUrl = resolveBaseUrl();
        return baseUrl + "/" + bucket + "/" + objectName;
    }

    private String resolveBaseUrl() {
        String baseUrl = StringUtils.hasText(minioProperties.getBaseUrl())
                ? minioProperties.getBaseUrl().trim()
                : minioProperties.getEndpoint().trim();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private boolean isImageFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return isImageFile(path.getFileName().toString());
    }

    private boolean isImageFile(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (String suffix : IMAGE_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private String detectMimeTypeByPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "application/octet-stream";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "text/markdown";
        }
        return "application/octet-stream";
    }

    private String extractFileNameFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "remote-image";
        }
        String candidate = url.trim();
        int queryPos = candidate.indexOf('?');
        if (queryPos >= 0) {
            candidate = candidate.substring(0, queryPos);
        }
        int fragmentPos = candidate.indexOf('#');
        if (fragmentPos >= 0) {
            candidate = candidate.substring(0, fragmentPos);
        }
        int slashPos = candidate.lastIndexOf('/');
        if (slashPos >= 0 && slashPos < candidate.length() - 1) {
            candidate = candidate.substring(slashPos + 1);
        }
        return StringUtils.hasText(candidate) ? candidate : "remote-image";
    }

    private Map<String, Object> mergeMap(Map<String, Object> left, Map<String, Object> right) {
        left.putAll(right);
        return left;
    }

    private void cleanupDirectory(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }
}
