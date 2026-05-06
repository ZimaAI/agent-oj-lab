package com.oj.agent.security.permission.service;

import com.oj.agent.security.permission.model.command.CreatePermissionCommand;
import com.oj.agent.security.permission.model.command.DeletePermissionCommand;
import com.oj.agent.security.permission.model.command.UpdatePermissionCommand;
import com.oj.agent.security.permission.model.query.PermissionGetQuery;
import com.oj.agent.security.permission.model.query.PermissionTreeQuery;
import com.oj.agent.security.permission.model.result.PermissionResult;

import java.util.List;

public interface PermissionService {

    PermissionResult createPermission(CreatePermissionCommand command);

    PermissionResult updatePermission(UpdatePermissionCommand command);

    List<PermissionResult> getPermissionTree(PermissionTreeQuery query);

    PermissionResult getPermissionById(PermissionGetQuery query);

    void deletePermission(DeletePermissionCommand command);
}
