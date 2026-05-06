package com.oj.agent.security.user.model.command;

import lombok.Data;

@Data
public class UserLoginLogCommand {

    private Long userId;

    private String username;

    private String clientIp;
}
