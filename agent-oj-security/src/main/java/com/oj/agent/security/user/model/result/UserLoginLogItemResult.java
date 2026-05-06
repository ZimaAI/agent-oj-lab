package com.oj.agent.security.user.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserLoginLogItemResult {

    private Long id;

    private Long userId;

    private String username;

    private String loginIp;

    private LocalDateTime loginTime;
}
