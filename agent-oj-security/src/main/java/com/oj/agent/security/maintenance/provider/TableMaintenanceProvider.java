package com.oj.agent.security.maintenance.provider;

import com.oj.agent.security.maintenance.model.result.TableClearResult;
import com.oj.agent.security.maintenance.model.result.TableOptionResult;

import java.util.List;

public interface TableMaintenanceProvider {

    List<TableOptionResult> listTables();

    TableClearResult clearTables(List<String> tableNames);
}
