package com.oj.agent.security.user.model.query;

import lombok.Data;

@Data
public class UserListQuery {

    private Integer userType;

    private Integer isActive;

    private Long current;

    private Long size;
}
