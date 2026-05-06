package com.oj.agent.admin.health.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminModuleInfoResponse {

    private String module;

    private String status;

    private String description;
}
