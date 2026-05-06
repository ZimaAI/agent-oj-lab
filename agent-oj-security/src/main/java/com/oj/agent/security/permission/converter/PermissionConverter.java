package com.oj.agent.security.permission.converter;

import com.oj.agent.security.permission.model.command.CreatePermissionCommand;
import com.oj.agent.security.permission.model.command.DeletePermissionCommand;
import com.oj.agent.security.permission.model.command.UpdatePermissionCommand;
import com.oj.agent.security.permission.model.query.PermissionGetQuery;
import com.oj.agent.security.permission.model.query.PermissionTreeQuery;
import com.oj.agent.security.permission.model.request.CreatePermissionRequest;
import com.oj.agent.security.permission.model.request.UpdatePermissionRequest;
import com.oj.agent.security.permission.model.response.PermissionResponse;
import com.oj.agent.security.permission.model.result.PermissionResult;

import java.util.Collections;
import java.util.List;

public final class PermissionConverter {

    private PermissionConverter() {
    }

    public static CreatePermissionCommand toCreateCommand(CreatePermissionRequest request) {
        CreatePermissionCommand command = new CreatePermissionCommand();
        if (request != null) {
            command.setPermissionCode(request.getPermissionCode());
            command.setPermissionName(request.getPermissionName());
            command.setPermissionType(request.getPermissionType());
            command.setParentId(request.getParentId());
            command.setDescription(request.getDescription());
        }
        return command;
    }

    public static UpdatePermissionCommand toUpdateCommand(Long permissionId, UpdatePermissionRequest request) {
        UpdatePermissionCommand command = new UpdatePermissionCommand();
        command.setPermissionId(permissionId);
        if (request != null) {
            command.setPermissionCode(request.getPermissionCode());
            command.setPermissionName(request.getPermissionName());
            command.setDescription(request.getDescription());
        }
        return command;
    }

    public static DeletePermissionCommand toDeleteCommand(Long permissionId) {
        DeletePermissionCommand command = new DeletePermissionCommand();
        command.setPermissionId(permissionId);
        return command;
    }

    public static PermissionGetQuery toGetQuery(Long permissionId) {
        PermissionGetQuery query = new PermissionGetQuery();
        query.setPermissionId(permissionId);
        return query;
    }

    public static PermissionTreeQuery toTreeQuery() {
        return new PermissionTreeQuery();
    }

    public static PermissionResponse toPermissionResponse(PermissionResult result) {
        if (result == null) {
            return null;
        }
        PermissionResponse response = new PermissionResponse();
        response.setId(result.getId());
        response.setPermissionCode(result.getPermissionCode());
        response.setPermissionName(result.getPermissionName());
        response.setPermissionType(result.getPermissionType());
        response.setParentId(result.getParentId());
        response.setDescription(result.getDescription());
        response.setChildren(toPermissionResponses(result.getChildren()));
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static List<PermissionResponse> toPermissionResponses(List<PermissionResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(PermissionConverter::toPermissionResponse)
                .toList();
    }
}
