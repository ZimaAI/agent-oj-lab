package com.oj.agent.core.file.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.file.model.command.FileUploadCommand;
import com.oj.agent.core.file.model.entity.FileRecord;
import com.oj.agent.core.file.model.query.FileGetQuery;
import com.oj.agent.core.file.model.query.FilePageQuery;
import com.oj.agent.core.file.model.request.FilePageRequest;
import com.oj.agent.core.file.model.request.FileUploadRequest;
import com.oj.agent.core.file.model.response.FileRecordResponse;
import com.oj.agent.core.file.model.result.FileRecordResult;

public final class FileRecordConverter {

    private FileRecordConverter() {
    }

    public static FileUploadCommand toUploadCommand(FileUploadRequest request) {
        if (request == null) {
            return null;
        }
        FileUploadCommand command = new FileUploadCommand();
        command.setFile(request.getFile());
        return command;
    }

    public static FilePageQuery toPageQuery(FilePageRequest request) {
        FilePageQuery query = new FilePageQuery();
        if (request == null) {
            return query;
        }
        query.setPageNum(request.getPageNum());
        query.setPageSize(request.getPageSize());
        return query;
    }

    public static FileGetQuery toGetQuery(Long id) {
        return new FileGetQuery(id);
    }

    public static FileRecordResult toResult(FileRecord entity) {
        if (entity == null) {
            return null;
        }
        FileRecordResult result = new FileRecordResult();
        result.setId(entity.getId());
        result.setOriginalFilename(entity.getOriginalFilename());
        result.setFileSize(entity.getFileSize());
        result.setContentType(entity.getContentType());
        result.setFileUrl(entity.getFileUrl());
        result.setBucketName(entity.getBucketName());
        result.setObjectName(entity.getObjectName());
        result.setCreateTime(entity.getCreateTime());
        return result;
    }

    public static FileRecordResponse toResponse(FileRecordResult result) {
        if (result == null) {
            return null;
        }
        FileRecordResponse response = new FileRecordResponse();
        response.setId(result.getId());
        response.setOriginalFilename(result.getOriginalFilename());
        response.setFileSize(result.getFileSize());
        response.setContentType(result.getContentType());
        response.setFileUrl(result.getFileUrl());
        response.setBucketName(result.getBucketName());
        response.setObjectName(result.getObjectName());
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static Page<FileRecordResponse> toResponsePage(Page<FileRecordResult> resultPage) {
        Page<FileRecordResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(FileRecordConverter::toResponse)
                .toList());
        return responsePage;
    }
}
