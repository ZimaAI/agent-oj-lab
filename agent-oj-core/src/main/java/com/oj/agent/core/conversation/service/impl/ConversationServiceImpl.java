package com.oj.agent.core.conversation.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.conversation.converter.ConversationConverter;
import com.oj.agent.core.conversation.mapper.ConversationMapper;
import com.oj.agent.core.conversation.mapper.ConversationMessageMapper;
import com.oj.agent.core.conversation.model.command.ConversationCurrentQuestionUpdateCommand;
import com.oj.agent.core.conversation.model.command.ConversationCreateCommand;
import com.oj.agent.core.conversation.model.command.ConversationDeleteCommand;
import com.oj.agent.core.conversation.model.command.ConversationTouchCommand;
import com.oj.agent.core.conversation.model.command.ConversationTitleBackfillCommand;
import com.oj.agent.core.conversation.model.entity.Conversation;
import com.oj.agent.core.conversation.model.query.ConversationGetQuery;
import com.oj.agent.core.conversation.model.query.ConversationListQuery;
import com.oj.agent.core.conversation.model.query.ConversationOwnershipQuery;
import com.oj.agent.core.conversation.model.result.ConversationCreateResult;
import com.oj.agent.core.conversation.model.result.ConversationDetailResult;
import com.oj.agent.core.conversation.model.result.ConversationListItemResult;
import com.oj.agent.core.conversation.service.ConversationService;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.conversation.util.ConversationAccessUtil;
import com.oj.agent.security.util.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private static final String DEFAULT_TITLE = "新会话";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final int PREVIEW_MAX_LENGTH = 80;
    private static final String ERROR_CURRENT_USER_REQUIRED = "当前用户未登录";

    private final ConversationMapper conversationMapper;
    private final AlgorithmQuestionService algorithmQuestionService;
    private final ConversationMessageMapper conversationMessageMapper;

    public ConversationServiceImpl(ConversationMapper conversationMapper,
                                   AlgorithmQuestionService algorithmQuestionService,
                                   ConversationMessageMapper conversationMessageMapper) {
        this.conversationMapper = conversationMapper;
        this.algorithmQuestionService = algorithmQuestionService;
        this.conversationMessageMapper = conversationMessageMapper;
    }

    /**
     * 创建当前用户的新会话并返回展示结果。
     */
    @Override
    public ConversationCreateResult createConversation(ConversationCreateCommand command) {
        Long userId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        Long currentQuestionId = command == null ? null : command.getCurrentQuestionId();
        String conversationId = command == null ? null : command.getConversationId();
        String title = resolveConversationTitle(currentQuestionId);

        Conversation conversation = ConversationConverter.toConversationEntity(
                userId,
                StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString(),
                title,
                ACTIVE_STATUS,
                command,
                now
        );
        if (conversationMapper.insert(conversation) <= 0) {
            throw new IllegalStateException("创建会话失败");
        }
        return ConversationConverter.toCreateResult(conversation);
    }

    /**
     * 分页查询当前用户会话并补充预览信息。
     */
    @Override
    public Page<ConversationListItemResult> listConversations(ConversationListQuery query) {
        ConversationListQuery effectiveQuery = query == null ? new ConversationListQuery() : query;
        validatePageParams(effectiveQuery.getCurrent(), effectiveQuery.getPageSize());
        Long userId = requireCurrentUserId();
        Page<Conversation> page = new Page<>(effectiveQuery.getCurrent(), effectiveQuery.getPageSize());
        IPage<Conversation> conversationPage = conversationMapper.selectPageByUserId(page, userId);

        Page<ConversationListItemResult> result = new Page<>(
                conversationPage.getCurrent(),
                conversationPage.getSize(),
                conversationPage.getTotal()
        );
        List<ConversationListItemResult> records = conversationPage.getRecords().stream()
                .map(this::toConversationListItemResult)
                .toList();
        result.setRecords(records);
        return result;
    }

    @Override
    public ConversationDetailResult getConversation(ConversationGetQuery query) {
        String conversationId = query == null ? null : query.getConversationId();
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        Long userId = requireCurrentUserId();
        ConversationOwnershipQuery ownershipQuery = new ConversationOwnershipQuery();
        ownershipQuery.setConversationId(conversationId);
        ownershipQuery.setUserId(userId);
        return validateConversationOwnership(ownershipQuery);
    }

    @Override
    public ConversationDetailResult validateConversationOwnership(ConversationOwnershipQuery query) {
        String conversationId = query == null ? null : query.getConversationId();
        Long userId = query == null ? null : query.getUserId();
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        Conversation conversation = requireOwnedConversation(conversationId, userId);
        return ConversationConverter.toDetailResult(conversation, buildPreview(conversationId));
    }

    @Override
    public boolean touchConversation(ConversationTouchCommand command) {
        if (command == null || !StringUtils.hasText(command.getConversationId()) || command.getLastMessageTime() == null) {
            return false;
        }
        return conversationMapper.updateLastMessageTimeByConversationId(
                command.getConversationId(),
                command.getLastMessageTime()
        ) > 0;
    }

    @Override
    public boolean updateCurrentQuestionIfLatestUserMessageMatches(ConversationCurrentQuestionUpdateCommand command) {
        if (command == null || !StringUtils.hasText(command.getConversationId())
                || command.getRequestSequenceNo() == null || command.getCurrentQuestionId() == null) {
            return false;
        }
        return conversationMapper.updateCurrentQuestionIdIfLatestUserMessageMatches(
                command.getConversationId(),
                command.getRequestSequenceNo(),
                command.getCurrentQuestionId()
        ) > 0;
    }

    @Override
    public boolean backfillTitleIfLatestUserMessageMatches(ConversationTitleBackfillCommand command) {
        if (command == null || !StringUtils.hasText(command.getConversationId())
                || command.getRequestSequenceNo() == null || !StringUtils.hasText(command.getTitle())) {
            return false;
        }
        return conversationMapper.updateTitleIfLatestUserMessageMatchesAndTitleDefault(
                command.getConversationId(),
                command.getRequestSequenceNo(),
                command.getTitle()
        ) > 0;
    }

    @Override
    public boolean deleteConversation(ConversationDeleteCommand command) {
        String conversationId = command == null ? null : command.getConversationId();
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        Long userId = requireCurrentUserId();
        requireOwnedConversation(conversationId, userId);
        return conversationMapper.softDeleteByConversationId(conversationId, userId) > 0;
    }

    // 转换会话列表项并补充最新消息预览。
    private ConversationListItemResult toConversationListItemResult(Conversation conversation) {
        return ConversationConverter.toListItemResult(
                conversation,
                buildPreview(conversation.getConversationId())
        );
    }

    // 解析创建会话时使用的标题。
    private String resolveConversationTitle(Long currentQuestionId) {
        if (currentQuestionId == null) {
            return DEFAULT_TITLE;
        }
        AlgorithmQuestionResult question;
        try {
            question = algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(currentQuestionId));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currentQuestionId 对应题目不存在");
        }
        if (question == null) {
            throw new IllegalArgumentException("currentQuestionId 对应题目不存在");
        }
        return question.getTitle();
    }

    // 生成侧边栏使用的最新消息预览。
    private String buildPreview(String conversationId) {
        String latestContent = conversationMessageMapper.selectLatestContentByConversationId(conversationId);
        if (latestContent == null || latestContent.isBlank()) {
            return "";
        }
        if (latestContent.length() <= PREVIEW_MAX_LENGTH) {
            return latestContent;
        }
        return latestContent.substring(0, PREVIEW_MAX_LENGTH);
    }

    // 校验分页参数为正整数。
    private void validatePageParams(long current, long pageSize) {
        if (current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("分页参数非法");
        }
    }

    private Conversation requireOwnedConversation(String conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectByConversationId(conversationId);
        return ConversationAccessUtil.requireOwnership(
                conversation,
                userId,
                IllegalArgumentException::new,
                IllegalArgumentException::new
        );
    }

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException(ERROR_CURRENT_USER_REQUIRED);
        }
        return userId;
    }
}
