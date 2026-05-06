package com.oj.agent.security.role.model.result;

import lombok.Data;

import java.util.List;

@Data
public class RolePageResult {

    private Long current;

    private Long size;

    private Long total;

    private Long pages;

    private List<RoleResult> records;
}
