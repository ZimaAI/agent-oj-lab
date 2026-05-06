package com.oj.agent.security.user.model.result;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserPermissionResult {

    private Long userId;

    private List<String> permissionCodes = new ArrayList<>();
}
