package com.oj.agent.security.auth.model.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(exclude = "password")
@EqualsAndHashCode(exclude = "password")
public class LoginCommand {

    private String username;

    private String password;

    private String clientIp;
}
