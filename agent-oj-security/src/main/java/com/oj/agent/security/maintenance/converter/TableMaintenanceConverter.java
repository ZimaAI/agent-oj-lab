package com.oj.agent.security.maintenance.converter;

import com.oj.agent.security.maintenance.model.command.TableClearCommand;
import com.oj.agent.security.maintenance.model.query.TableListQuery;
import com.oj.agent.security.maintenance.model.request.TableClearRequest;
import com.oj.agent.security.maintenance.model.response.TableClearResponse;
import com.oj.agent.security.maintenance.model.response.TableOptionResponse;
import com.oj.agent.security.maintenance.model.result.TableClearResult;
import com.oj.agent.security.maintenance.model.result.TableOptionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TableMaintenanceConverter {

    private TableMaintenanceConverter() {
    }

    public static TableListQuery toTableListQuery() {
        return new TableListQuery();
    }

    public static TableClearCommand toClearCommand(TableClearRequest request) {
        TableClearCommand command = new TableClearCommand();
        if (request != null) {
            command.setTableNames(request.getTableNames() == null ? null : new ArrayList<>(request.getTableNames()));
        }
        return command;
    }

    public static List<TableOptionResponse> toTableOptionResponses(List<TableOptionResult> results) {
        if (results == null) {
            return Collections.emptyList();
        }
        return results.stream()
                .filter(Objects::nonNull)
                .map(TableMaintenanceConverter::toTableOptionResponse)
                .toList();
    }

    public static TableOptionResponse toTableOptionResponse(TableOptionResult result) {
        if (result == null) {
            return null;
        }
        TableOptionResponse response = new TableOptionResponse();
        response.setDatasourceType(result.getDatasourceType());
        response.setTableName(result.getTableName());
        response.setDisplayName(result.getDisplayName());
        response.setDescription(result.getDescription());
        response.setResetStrategy(result.getResetStrategy());
        response.setEnabled(result.getEnabled());
        return response;
    }

    public static TableClearResponse toTableClearResponse(TableClearResult result) {
        if (result == null) {
            return null;
        }
        TableClearResponse response = new TableClearResponse();
        response.setSuccessCount(result.getSuccessCount());
        response.setFailureCount(result.getFailureCount());
        response.setResults(toTableClearResponseItems(result.getResults()));
        return response;
    }

    private static List<TableClearResponse.ResultItem> toTableClearResponseItems(List<TableClearResult.ResultItem> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(TableMaintenanceConverter::toTableClearResponseItem)
                .toList();
    }

    private static TableClearResponse.ResultItem toTableClearResponseItem(TableClearResult.ResultItem item) {
        if (item == null) {
            return null;
        }
        return new TableClearResponse.ResultItem(
                item.getTableName(),
                item.getDatasourceType(),
                item.getSuccess(),
                item.getMessage()
        );
    }
}
