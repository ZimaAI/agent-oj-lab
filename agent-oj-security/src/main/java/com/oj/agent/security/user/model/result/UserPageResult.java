package com.oj.agent.security.user.model.result;

import lombok.Data;

import java.util.List;

@Data
public class UserPageResult {

    private Long current;

    private Long size;

    private Long total;

    private List<UserResult> records;
}
