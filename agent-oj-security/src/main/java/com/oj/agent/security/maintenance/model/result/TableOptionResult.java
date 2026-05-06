package com.oj.agent.security.maintenance.model.result;

import lombok.Data;

@Data
public class TableOptionResult {

    private String datasourceType;

    private String tableName;

    private String displayName;

    private String description;

    private String resetStrategy;

    private Boolean enabled;
}
