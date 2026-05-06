package com.oj.agent.admin.user.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminLoginLogItemResult {

    private Long id;

    private Long userId;

    private String username;

    private String loginIp;

    private LocalDateTime loginTime;
}
