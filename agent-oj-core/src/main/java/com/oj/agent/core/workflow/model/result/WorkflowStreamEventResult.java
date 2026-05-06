package com.oj.agent.core.workflow.model.result;

import com.oj.agent.core.workflow.enums.TextType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStreamEventResult {

    private String conversationId;

    private String nodeName;

    private TextType textType;

    private String text;

    @Builder.Default
    private boolean error = false;

    @Builder.Default
    private boolean complete = false;

    public static WorkflowStreamEventResult error(String conversationId, String nodeName, String text) {
        return WorkflowStreamEventResult.builder()
                .conversationId(conversationId)
                .nodeName(nodeName)
                .text(text)
                .error(true)
                .textType(TextType.TEXT)
                .build();
    }

    public static WorkflowStreamEventResult complete(String conversationId, String nodeName) {
        return WorkflowStreamEventResult.builder()
                .conversationId(conversationId)
                .nodeName(nodeName)
                .complete(true)
                .textType(TextType.TEXT)
                .build();
    }
}
