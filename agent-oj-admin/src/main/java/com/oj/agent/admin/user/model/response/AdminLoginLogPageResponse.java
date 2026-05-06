package com.oj.agent.admin.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginLogPageResponse {

    private Long current;

    private Long size;

    private Long total;

    private List<AdminLoginLogItemResponse> records;
}
