package com.oj.agent.admin.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginLogItemResponse {

    private Long id;

    private Long userId;

    private String username;

    private String loginIp;

    private LocalDateTime loginTime;
}
