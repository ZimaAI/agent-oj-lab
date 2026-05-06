package com.oj.agent.core.file.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.file.model.command.FileUploadCommand;
import com.oj.agent.core.file.model.query.FileGetQuery;
import com.oj.agent.core.file.model.query.FilePageQuery;
import com.oj.agent.core.file.model.result.FileRecordResult;

public interface FileService {

    FileRecordResult upload(FileUploadCommand command);

    Page<FileRecordResult> pageCurrentUserFiles(FilePageQuery query);

    FileRecordResult getCurrentUserFile(FileGetQuery query);

    void delete(FileGetQuery query);
}
