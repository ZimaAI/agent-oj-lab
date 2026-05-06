package com.oj.agent.core.file.model.query;

import lombok.Data;

@Data
public class FilePageQuery {

    private long pageNum = 1;

    private long pageSize = 20;
}
