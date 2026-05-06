package com.oj.agent.core.workflow.model.result;

import com.oj.agent.core.conversation.model.result.ConversationDetailResult;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;
import com.oj.agent.core.workflow.enums.UserIntentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowBootstrapResult {

    private ConversationDetailResult conversation;

    private AlgorithmQuestionResult currentAlgorithmQuestionSnapshot;

    private CodeSubmissionResult currentCodeSubmission;

    private UserIntentType presetIntent;

    private Integer userMessageSequenceNo;
}
