package com.oj.agent.core.conversation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.conversation.model.command.ConversationMessageCreateCommand;
import com.oj.agent.core.conversation.model.query.ConversationMessageListQuery;
import com.oj.agent.core.conversation.model.result.ConversationMessageCreateResult;
import com.oj.agent.core.conversation.model.result.ConversationMessageResult;

public interface ConversationMessageService {

    /**
     * 创建消息并自动分配会话内顺序号。
     */
    ConversationMessageCreateResult createMessage(ConversationMessageCreateCommand command);

    /**
     * 分页查询当前用户可访问的会话消息。
     */
    Page<ConversationMessageResult> listMessages(ConversationMessageListQuery query);

}
