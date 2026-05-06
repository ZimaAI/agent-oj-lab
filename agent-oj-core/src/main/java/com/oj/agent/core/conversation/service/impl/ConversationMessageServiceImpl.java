package com.oj.agent.core.conversation.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.conversation.converter.ConversationConverter;
import com.oj.agent.core.conversation.mapper.ConversationMapper;
import com.oj.agent.core.conversation.mapper.ConversationMessageMapper;
import com.oj.agent.core.conversation.model.command.ConversationMessageCreateCommand;
import com.oj.agent.core.conversation.model.entity.Conversation;
import com.oj.agent.core.conversation.model.entity.ConversationMessage;
import com.oj.agent.core.conversation.model.query.ConversationMessageListQuery;
import com.oj.agent.core.conversation.model.result.ConversationMessageCreateResult;
import com.oj.agent.core.conversation.model.result.ConversationMessageResult;
import com.oj.agent.core.conversation.service.ConversationMessageService;
import com.oj.agent.core.conversation.util.ConversationAccessUtil;
import com.oj.agent.security.util.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ConversationMessageServiceImpl extends ServiceImpl<ConversationMessageMapper, ConversationMessage>
        implements ConversationMessageService {

    private static final int RESULT_SUMMARY_MAX_LENGTH = 120;
    private static final String ERROR_CURRENT_USER_REQUIRED = "当前用户未登录";

    private final ConversationMapper conversationMapper;

    public ConversationMessageServiceImpl(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    /**
     * 分页查询当前用户可访问的会话消息。
     */
    @Override
    public Page<ConversationMessageResult> listMessages(ConversationMessageListQuery query) {
        ConversationMessageListQuery effectiveQuery = query == null ? new ConversationMessageListQuery() : query;
        validateConversationId(effectiveQuery.getConversationId());
        validatePageParams(effectiveQuery.getCurrent(), effectiveQuery.getPageSize());
        Long userId = requireCurrentUserId();
        validateConversationOwnership(effectiveQuery.getConversationId(), userId);

        Page<ConversationMessage> page = new Page<>(effectiveQuery.getCurrent(), effectiveQuery.getPageSize());
        IPage<ConversationMessage> messagePage = baseMapper.selectPageByConversationId(
                page,
                effectiveQuery.getConversationId()
        );
        Page<ConversationMessageResult> result = new Page<>(
                messagePage.getCurrent(),
                messagePage.getSize(),
                messagePage.getTotal()
        );
        List<ConversationMessageResult> records = messagePage.getRecords().stream()
                .map(this::toConversationMessageResult)
                .toList();
        result.setRecords(records);
        return result;
    }

    /**
     * 创建消息并自动分配会话内顺序号。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationMessageCreateResult createMessage(ConversationMessageCreateCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        ConversationMessage message = ConversationConverter.toMessageEntity(command);
        prepareMessageForAutoSequenceNoSave(message);
        if (!this.save(message)) {
            throw new IllegalStateException("保存消息失败");
        }
        return ConversationConverter.toMessageCreateResult(message);
    }

    // 转换消息展示结果对象。
    private ConversationMessageResult toConversationMessageResult(ConversationMessage message) {
        return ConversationConverter.toMessageResult(message, buildResultSummary(message));
    }

    // 校验会话归属当前用户。
    private Conversation validateConversationOwnership(String conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectByConversationId(conversationId);
        return ConversationAccessUtil.requireOwnership(
                conversation,
                userId,
                IllegalArgumentException::new,
                IllegalArgumentException::new
        );
    }

    // 统一处理顺序号分配与时间填充，供新旧入口共享。
    private void prepareMessageForAutoSequenceNoSave(ConversationMessage message) {
        Objects.requireNonNull(message, "message 不能为空");
        validateConversationId(message.getConversationId());
        Long lockedConversationId = conversationMapper.lockByConversationId(message.getConversationId());
        if (lockedConversationId == null) {
            throw new IllegalArgumentException("conversationId 不存在");
        }
        Integer maxSequenceNo = baseMapper.selectMaxSequenceNo(message.getConversationId());
        message.setSequenceNo((maxSequenceNo == null ? 0 : maxSequenceNo) + 1);
        LocalDateTime now = LocalDateTime.now();
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }
        if (message.getUpdateTime() == null) {
            message.setUpdateTime(now);
        }
    }

    // 统一生成消息结果摘要。
    private String buildResultSummary(ConversationMessage message) {
        if (message.getResultType() == null || message.getResultType().isBlank()) {
            return null;
        }
        if (message.getResultData() == null || message.getResultData().isBlank()) {
            return null;
        }
        String summary = message.getResultType() + ": " + message.getResultData();
        if (summary.length() <= RESULT_SUMMARY_MAX_LENGTH) {
            return summary;
        }
        return summary.substring(0, RESULT_SUMMARY_MAX_LENGTH);
    }

    // 校验会话标识不为空白。
    private void validateConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
    }

    // 校验分页参数为正整数。
    private void validatePageParams(long current, long pageSize) {
        if (current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("分页参数非法");
        }
    }

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException(ERROR_CURRENT_USER_REQUIRED);
        }
        return userId;
    }
}
