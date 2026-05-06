package com.oj.agent.security.auth.model.request;

import lombok.Data;

@Data
public class RefreshTokenRequest {

    private String refreshToken;
}
