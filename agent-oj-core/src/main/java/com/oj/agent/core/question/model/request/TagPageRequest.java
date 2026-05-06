package com.oj.agent.core.question.model.request;

import lombok.Data;

@Data
public class TagPageRequest {

    // 当前页码。
    private long current = 1;

    // 分页大小。
    private long pageSize = 20;

    // 标签关键词。
    private String keyword;
}
