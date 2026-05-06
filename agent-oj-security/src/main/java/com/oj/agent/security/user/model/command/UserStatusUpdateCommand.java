package com.oj.agent.security.user.model.command;

import lombok.Data;

@Data
public class UserStatusUpdateCommand {

    private Long userId;

    private Integer isActive;
}
