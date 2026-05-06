package com.oj.agent.admin.question.hitk.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTestCreateCommand {

    private Long questionId;

    private Long docId;

    private List<Long> segmentIds;
}
