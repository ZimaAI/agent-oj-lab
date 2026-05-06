package com.oj.agent.core.file.model.command;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileUploadCommand {

    private MultipartFile file;
}
