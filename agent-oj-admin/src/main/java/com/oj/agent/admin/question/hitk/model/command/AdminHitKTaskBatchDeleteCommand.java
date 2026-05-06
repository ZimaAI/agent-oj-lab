package com.oj.agent.admin.question.hitk.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskBatchDeleteCommand {

    private List<Long> taskIds;
}
