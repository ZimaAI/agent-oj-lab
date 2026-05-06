package com.oj.agent.core.file.model.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileUploadRequest {

    private MultipartFile file;
}
