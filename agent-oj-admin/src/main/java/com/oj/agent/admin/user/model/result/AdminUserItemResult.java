package com.oj.agent.admin.user.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserItemResult {

    private Long id;

    private String username;

    private Integer userType;

    private Integer needPassword;

    private Integer isActive;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer trialCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
