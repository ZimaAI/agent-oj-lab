package com.oj.agent.security.maintenance.controller;

import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import com.oj.agent.security.maintenance.converter.TableMaintenanceConverter;
import com.oj.agent.security.maintenance.model.request.TableClearRequest;
import com.oj.agent.security.maintenance.model.response.TableClearResponse;
import com.oj.agent.security.maintenance.model.response.TableOptionResponse;
import com.oj.agent.security.maintenance.service.TableMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/table-maintenance")
@RequiredArgsConstructor
public class TableMaintenanceController {

    private final TableMaintenanceService tableMaintenanceService;

    @GetMapping("/tables")
    @RequirePermission("table:clear")
    public Result<List<TableOptionResponse>> listTables() {
        return Result.success(TableMaintenanceConverter.toTableOptionResponses(
                tableMaintenanceService.listTables(TableMaintenanceConverter.toTableListQuery())
        ));
    }

    @PostMapping("/clear")
    @RequirePermission("table:clear")
    public Result<TableClearResponse> clearTables(@RequestBody TableClearRequest request) {
        return Result.success(TableMaintenanceConverter.toTableClearResponse(
                tableMaintenanceService.clearTables(TableMaintenanceConverter.toClearCommand(request))
        ));
    }
}
