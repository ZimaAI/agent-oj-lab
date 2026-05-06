package com.oj.agent.security.user.model.result;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
public class UserAuthResult {

    private Long id;

    private String username;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String password;

    private Integer userType;

    private Integer needPassword;

    private Integer isActive;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer trialCount;
}
