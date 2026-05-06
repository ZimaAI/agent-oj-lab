package com.oj.agent.security.user.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResult {

    private Long id;

    private String username;

    private Integer userType;

    private Integer needPassword;

    private Integer isActive;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer trialCount;

    private List<RoleInfoResult> roles;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfoResult {
        private Long roleId;
        private String roleName;
        private String roleCode;
    }
}
