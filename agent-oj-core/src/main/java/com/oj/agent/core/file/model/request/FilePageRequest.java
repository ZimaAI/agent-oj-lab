package com.oj.agent.core.file.model.request;

import lombok.Data;

@Data
public class FilePageRequest {

    private long pageNum = 1;

    private long pageSize = 20;
}
