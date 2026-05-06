package com.oj.agent.security.auth.model.result;

import lombok.Data;

import java.util.List;

@Data
public class AuthResult {

    private Long userId;

    private String username;

    private Integer userType;

    private List<String> permissions;

    private String accessToken;

    private String refreshToken;
}
