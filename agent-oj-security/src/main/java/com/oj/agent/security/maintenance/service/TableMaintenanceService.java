package com.oj.agent.security.maintenance.service;

import com.oj.agent.security.maintenance.model.command.TableClearCommand;
import com.oj.agent.security.maintenance.model.query.TableListQuery;
import com.oj.agent.security.maintenance.model.result.TableClearResult;
import com.oj.agent.security.maintenance.model.result.TableOptionResult;

import java.util.List;

public interface TableMaintenanceService {

    List<TableOptionResult> listTables(TableListQuery query);

    TableClearResult clearTables(TableClearCommand command);
}
