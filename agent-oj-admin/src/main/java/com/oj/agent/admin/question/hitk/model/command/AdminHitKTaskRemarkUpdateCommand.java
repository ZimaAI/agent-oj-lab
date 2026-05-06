package com.oj.agent.admin.question.hitk.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskRemarkUpdateCommand {

    private Long taskId;

    private String remark;
}
