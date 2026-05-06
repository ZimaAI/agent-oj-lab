package com.oj.agent.security.user.model.request;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpdateUserRequest {

    private String password;

    private Integer needPassword;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer trialCount;

    private List<Long> roleIds;
}
