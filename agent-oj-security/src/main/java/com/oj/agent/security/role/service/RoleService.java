package com.oj.agent.security.role.service;

import com.oj.agent.security.role.model.command.BindRolePermissionsCommand;
import com.oj.agent.security.role.model.command.CreateRoleCommand;
import com.oj.agent.security.role.model.command.DeleteRoleCommand;
import com.oj.agent.security.role.model.command.UpdateRoleCommand;
import com.oj.agent.security.role.model.query.RoleGetQuery;
import com.oj.agent.security.role.model.query.RolePageQuery;
import com.oj.agent.security.role.model.result.RolePageResult;
import com.oj.agent.security.role.model.result.RoleResult;

public interface RoleService {

    RoleResult createRole(CreateRoleCommand command);

    RoleResult updateRole(UpdateRoleCommand command);

    RolePageResult listRoles(RolePageQuery query);

    RoleResult getRoleById(RoleGetQuery query);

    void deleteRole(DeleteRoleCommand command);

    void bindPermissions(BindRolePermissionsCommand command);
}
