package com.oj.agent.security.maintenance.model.request;

import lombok.Data;

import java.util.List;

@Data
public class TableClearRequest {

    private List<String> tableNames;
}
