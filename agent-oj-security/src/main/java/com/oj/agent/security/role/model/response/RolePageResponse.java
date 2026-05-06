package com.oj.agent.security.role.model.response;

import lombok.Data;

import java.util.List;

@Data
public class RolePageResponse {

    private Long current;

    private Long size;

    private Long total;

    private Long pages;

    private List<RoleResponse> records;
}
