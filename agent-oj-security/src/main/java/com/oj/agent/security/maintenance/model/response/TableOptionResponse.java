package com.oj.agent.security.maintenance.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableOptionResponse {

    private String datasourceType;

    private String tableName;

    private String displayName;

    private String description;

    private String resetStrategy;

    private Boolean enabled;
}
