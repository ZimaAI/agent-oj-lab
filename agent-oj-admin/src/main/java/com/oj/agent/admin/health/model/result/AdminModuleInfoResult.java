package com.oj.agent.admin.health.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminModuleInfoResult {

    private String module;

    private String status;

    private String description;
}
