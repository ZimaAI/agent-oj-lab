package com.oj.agent.security.user.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CurrentUserResponse {

    private Long id;

    private String username;

    private Integer userType;

    private Integer isActive;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer trialCount;
}
