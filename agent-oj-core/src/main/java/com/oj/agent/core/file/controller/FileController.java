package com.oj.agent.core.file.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.common.api.Result;
import com.oj.agent.core.file.converter.FileRecordConverter;
import com.oj.agent.core.file.model.request.FilePageRequest;
import com.oj.agent.core.file.model.request.FileUploadRequest;
import com.oj.agent.core.file.model.response.FileRecordResponse;
import com.oj.agent.core.file.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileRecordResponse> upload(@ModelAttribute FileUploadRequest request) {
        return Result.success(FileRecordConverter.toResponse(
                fileService.upload(FileRecordConverter.toUploadCommand(request))
        ));
    }

    @GetMapping("/page")
    public Result<Page<FileRecordResponse>> page(@ModelAttribute FilePageRequest request) {
        return Result.success(FileRecordConverter.toResponsePage(
                fileService.pageCurrentUserFiles(FileRecordConverter.toPageQuery(request))
        ));
    }

    @GetMapping("/{id}")
    public Result<FileRecordResponse> getById(@PathVariable Long id) {
        return Result.success(FileRecordConverter.toResponse(
                fileService.getCurrentUserFile(FileRecordConverter.toGetQuery(id))
        ));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(FileRecordConverter.toGetQuery(id));
        return Result.success();
    }
}
