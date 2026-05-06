package com.oj.agent.admin.user.model.result;

import lombok.Data;

import java.util.List;

@Data
public class AdminUserPageResult {

    private Long current;

    private Long size;

    private Long total;

    private List<AdminUserItemResult> records;
}
