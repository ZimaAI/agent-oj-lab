package com.oj.agent.security.user.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CurrentUserResult {

    private Long id;

    private String username;

    private Integer userType;

    private Integer isActive;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer trialCount;
}
