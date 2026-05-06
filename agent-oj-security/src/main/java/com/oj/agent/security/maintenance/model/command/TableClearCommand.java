package com.oj.agent.security.maintenance.model.command;

import lombok.Data;

import java.util.List;

@Data
public class TableClearCommand {

    private List<String> tableNames;
}
