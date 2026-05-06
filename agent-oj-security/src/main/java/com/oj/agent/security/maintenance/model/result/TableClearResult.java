package com.oj.agent.security.maintenance.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableClearResult {

    private Integer successCount;

    private Integer failureCount;

    private List<ResultItem> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItem {

        private String tableName;

        private String datasourceType;

        private Boolean success;

        private String message;
    }
}
