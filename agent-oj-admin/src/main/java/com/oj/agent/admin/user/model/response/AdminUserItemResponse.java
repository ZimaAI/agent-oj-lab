package com.oj.agent.admin.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserItemResponse {

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
