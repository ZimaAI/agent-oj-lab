package com.oj.agent.security.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;

    private String username;

    private Integer userType;

    private Integer needPassword;

    private Integer isActive;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer trialCount;

    private List<RoleInfo> roles;

    private LocalDateTime createTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private Long roleId;
        private String roleName;
        private String roleCode;
    }
}
