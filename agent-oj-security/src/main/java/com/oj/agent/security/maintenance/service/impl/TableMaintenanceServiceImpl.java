package com.oj.agent.security.maintenance.service.impl;

import com.oj.agent.security.exception.ProviderUnavailableException;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.exception.ValidationException;
import com.oj.agent.security.maintenance.model.command.TableClearCommand;
import com.oj.agent.security.maintenance.model.query.TableListQuery;
import com.oj.agent.security.maintenance.model.result.TableClearResult;
import com.oj.agent.security.maintenance.model.result.TableOptionResult;
import com.oj.agent.security.maintenance.provider.TableMaintenanceProvider;
import com.oj.agent.security.maintenance.service.TableMaintenanceService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class TableMaintenanceServiceImpl implements TableMaintenanceService {

    private final TableMaintenanceProvider tableMaintenanceProvider;

    public TableMaintenanceServiceImpl(@Nullable TableMaintenanceProvider tableMaintenanceProvider) {
        this.tableMaintenanceProvider = tableMaintenanceProvider;
    }

    @Override
    public List<TableOptionResult> listTables(TableListQuery query) {
        return getProviderOrThrow().listTables();
    }

    @Override
    public TableClearResult clearTables(TableClearCommand command) {
        TableMaintenanceProvider provider = getProviderOrThrow();
        List<String> normalizedTableNames = normalizeTableNames(command);
        validateAgainstWhitelist(normalizedTableNames, provider.listTables());
        return provider.clearTables(normalizedTableNames);
    }

    private List<String> normalizeTableNames(TableClearCommand command) {
        if (command == null || command.getTableNames() == null || command.getTableNames().isEmpty()) {
            throw validationException();
        }

        if (command.getTableNames().stream().anyMatch(Objects::isNull)) {
            throw validationException();
        }

        List<String> normalized = command.getTableNames().stream()
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));

        if (normalized.stream().anyMatch(String::isEmpty)) {
            throw validationException();
        }
        return normalized;
    }

    private void validateAgainstWhitelist(List<String> tableNames, List<TableOptionResult> options) {
        Set<String> allowedTables = safeOptions(options).stream()
                .filter(Objects::nonNull)
                .filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                .map(TableOptionResult::getTableName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(java.util.stream.Collectors.toSet());

        boolean hasInvalidTable = tableNames.stream().anyMatch(tableName -> !allowedTables.contains(tableName));
        if (hasInvalidTable) {
            throw validationException();
        }
    }

    private List<TableOptionResult> safeOptions(List<TableOptionResult> options) {
        return options == null ? Collections.emptyList() : options;
    }

    private TableMaintenanceProvider getProviderOrThrow() {
        if (tableMaintenanceProvider == null) {
            throw new ProviderUnavailableException(
                    SecurityErrorCode.TABLE_002.getCode(),
                    SecurityErrorCode.TABLE_002.getMessage()
            );
        }
        return tableMaintenanceProvider;
    }

    private ValidationException validationException() {
        return new ValidationException(
                SecurityErrorCode.TABLE_001.getCode(),
                SecurityErrorCode.TABLE_001.getMessage()
        );
    }
}
