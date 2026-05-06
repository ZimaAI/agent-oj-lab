package com.oj.agent.security.auth.model.command;

import lombok.Data;

@Data
public class RefreshTokenCommand {

    private String refreshToken;
}
