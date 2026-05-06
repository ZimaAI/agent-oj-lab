package com.oj.agent.core.workflow.service;

import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.conversation.model.command.ConversationCreateCommand;
import com.oj.agent.core.conversation.model.command.ConversationMessageCreateCommand;
import com.oj.agent.core.conversation.model.command.ConversationTouchCommand;
import com.oj.agent.core.conversation.model.query.ConversationOwnershipQuery;
import com.oj.agent.core.conversation.model.result.ConversationCreateResult;
import com.oj.agent.core.conversation.model.result.ConversationDetailResult;
import com.oj.agent.core.conversation.model.result.ConversationMessageCreateResult;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.submission.model.command.CodeSubmissionCreateCommand;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;
import com.oj.agent.core.workflow.model.command.WorkflowStartCommand;
import com.oj.agent.core.workflow.model.result.WorkflowBootstrapResult;
import com.oj.agent.core.conversation.service.ConversationMessageService;
import com.oj.agent.core.conversation.service.ConversationService;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.submission.service.CodeSubmissionService;
import com.oj.agent.security.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WorkflowBootstrapService {

    private static final String MESSAGE_SENDER_USER = "USER";
    private static final String MESSAGE_TYPE_PLAIN = "PLAIN";
    private static final String ERROR_BOOTSTRAP_USER_MISSING = "UserContext.getUserId() 不能为空，无法初始化会话";

    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;
    private final AlgorithmQuestionService algorithmQuestionService;
    private final CodeSubmissionService codeSubmissionService;

    /**
     * 在图执行前事务性初始化会话、用户消息与当前题目快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowBootstrapResult bootstrap(WorkflowStartCommand command,
                                             boolean isNewConversation,
                                             String conversationId,
                                             String traceId) {
        Long userId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        ConversationDetailResult conversation = isNewConversation
                ? createConversation(conversationId, userId)
                : validateAndGetConversation(conversationId, userId);

        ConversationMessageCreateResult userMessage = saveUserConversationMessage(
                conversationId,
                command.getMessage(),
                traceId
        );
        updateConversationLastMessageTime(conversationId, now);

        return WorkflowBootstrapResult.builder()
                .conversation(conversation)
                .currentAlgorithmQuestionSnapshot(loadCurrentQuestionSnapshot(conversation))
                .userMessageSequenceNo(userMessage.getSequenceNo())
                .build();
    }

    /**
     * 在图执行前事务性初始化代码评审所需的会话、消息、题目快照与提交记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowBootstrapResult bootstrapCodeEvaluation(WorkflowStartCommand command,
                                                           String conversationId,
                                                           String traceId) {
        Long userId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        ConversationDetailResult conversation = validateAndGetConversation(conversationId, userId);
        Long currentQuestionId = requireCurrentQuestionId(conversation);
        AlgorithmQuestionResult currentQuestionSnapshot = loadRequiredCurrentQuestionSnapshot(currentQuestionId);
        ConversationMessageCreateResult userMessage = saveUserConversationMessage(
                conversationId,
                resolveCodeEvaluationMessage(command),
                traceId
        );
        updateConversationLastMessageTime(conversationId, now);
        CodeSubmissionResult codeSubmission = createCodeSubmission(command, conversationId, traceId, userId,
                currentQuestionId, userMessage.getId());

        return WorkflowBootstrapResult.builder()
                .conversation(conversation)
                .currentAlgorithmQuestionSnapshot(currentQuestionSnapshot)
                .currentCodeSubmission(codeSubmission)
                .presetIntent(UserIntentType.CODE_EVALUATION)
                .userMessageSequenceNo(userMessage.getSequenceNo())
                .build();
    }

    // 创建代码提交并返回持久化前后保持同一引用的对象。
    private CodeSubmissionResult createCodeSubmission(WorkflowStartCommand command,
                                                      String conversationId,
                                                      String traceId,
                                                      Long userId,
                                                      Long algorithmQuestionId,
                                                      Long conversationMessageId) {
        String codeContent = requireCodeContent(command);
        if (!StringUtils.hasText(command.getLanguage())) {
            throw new IllegalArgumentException("language cannot be empty");
        }
        CodeSubmissionCreateCommand createCommand = new CodeSubmissionCreateCommand();
        createCommand.setUserId(userId);
        createCommand.setAlgorithmQuestionId(algorithmQuestionId);
        createCommand.setCode(codeContent);
        createCommand.setLanguage(command.getLanguage());
        createCommand.setConversationId(conversationId);
        createCommand.setConversationMessageId(conversationMessageId);
        createCommand.setTraceId(traceId);
        return codeSubmissionService.createPendingSubmission(createCommand);
    }

    private String requireCodeContent(WorkflowStartCommand command) {
        String code = command == null ? null : command.getCode();
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code cannot be empty");
        }
        return code;
    }

    private String resolveCodeEvaluationMessage(WorkflowStartCommand command) {
        if (command == null) {
            return null;
        }
        if (StringUtils.hasText(command.getMessage())) {
            return command.getMessage();
        }
        return requireCodeContent(command);
    }

    // 创建新会话并返回持久化后的会话对象。
    private ConversationDetailResult createConversation(String conversationId, Long userId) {
        ConversationCreateCommand command = new ConversationCreateCommand();
        command.setConversationId(conversationId);
        ConversationCreateResult created = conversationService.createConversation(command);
        ConversationOwnershipQuery query = new ConversationOwnershipQuery();
        query.setConversationId(created.getConversationId());
        query.setUserId(userId);
        return conversationService.validateConversationOwnership(query);
    }

    // 校验会话最后题目必须存在。
    private Long requireCurrentQuestionId(ConversationDetailResult conversation) {
        if (conversation == null || conversation.getCurrentQuestionId() == null) {
            throw new IllegalStateException("conversation.currentQuestion 不能为空，无法启动代码评审");
        }
        return conversation.getCurrentQuestionId();
    }

    // 校验会话存在且归属当前用户。
    private ConversationDetailResult validateAndGetConversation(String conversationId, Long userId) {
        ConversationOwnershipQuery query = new ConversationOwnershipQuery();
        query.setConversationId(conversationId);
        query.setUserId(userId);
        return conversationService.validateConversationOwnership(query);
    }

    // 加载请求开始时的当前题目快照。
    private AlgorithmQuestionResult loadCurrentQuestionSnapshot(ConversationDetailResult conversation) {
        if (conversation == null || conversation.getCurrentQuestionId() == null) {
            return null;
        }
        try {
            return algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(conversation.getCurrentQuestionId()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // 加载代码评审必需的当前题目快照。
    private AlgorithmQuestionResult loadRequiredCurrentQuestionSnapshot(Long currentQuestionId) {
        try {
            return algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(currentQuestionId));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("currentQuestion 对应题目不存在，questionId=" + currentQuestionId);
        }
    }

    // 保存用户会话消息并返回带序号的消息对象。
    private ConversationMessageCreateResult saveUserConversationMessage(String conversationId,
                                                                       String messageContent,
                                                                       String traceId) {
        ConversationMessageCreateCommand command = new ConversationMessageCreateCommand();
        command.setConversationId(conversationId);
        command.setSender(MESSAGE_SENDER_USER);
        command.setMessageType(MESSAGE_TYPE_PLAIN);
        command.setContent(messageContent);
        command.setTraceId(traceId);
        return conversationMessageService.createMessage(command);
    }

    // 更新会话最后消息时间。
    private void updateConversationLastMessageTime(String conversationId, LocalDateTime now) {
        ConversationTouchCommand command = new ConversationTouchCommand();
        command.setConversationId(conversationId);
        command.setLastMessageTime(now);
        boolean lastMessageTimeUpdated = conversationService.touchConversation(command);
        if (!lastMessageTimeUpdated) {
            throw new IllegalStateException("更新会话最后消息时间失败，conversationId=" + conversationId);
        }
    }

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException(ERROR_BOOTSTRAP_USER_MISSING);
        }
        return userId;
    }

}
