package com.oj.agent.security.user.model.response;

import lombok.Data;

import java.util.List;

@Data
public class UserPageResponse {

    private Long current;

    private Long size;

    private Long total;

    private List<UserResponse> records;
}
