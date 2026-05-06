package com.oj.agent.core.conversation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.conversation.model.command.ConversationCurrentQuestionUpdateCommand;
import com.oj.agent.core.conversation.model.command.ConversationCreateCommand;
import com.oj.agent.core.conversation.model.command.ConversationDeleteCommand;
import com.oj.agent.core.conversation.model.command.ConversationTouchCommand;
import com.oj.agent.core.conversation.model.command.ConversationTitleBackfillCommand;
import com.oj.agent.core.conversation.model.query.ConversationGetQuery;
import com.oj.agent.core.conversation.model.query.ConversationListQuery;
import com.oj.agent.core.conversation.model.query.ConversationOwnershipQuery;
import com.oj.agent.core.conversation.model.result.ConversationCreateResult;
import com.oj.agent.core.conversation.model.result.ConversationDetailResult;
import com.oj.agent.core.conversation.model.result.ConversationListItemResult;

public interface ConversationService {

    /**
     * 创建当前登录用户的新会话。
     */
    ConversationCreateResult createConversation(ConversationCreateCommand command);

    /**
     * 分页查询当前登录用户的会话列表。
     */
    Page<ConversationListItemResult> listConversations(ConversationListQuery query);

    /**
     * 查询当前登录用户的会话详情。
     */
    ConversationDetailResult getConversation(ConversationGetQuery query);

    /**
     * 按显式归属条件查询会话详情。
     */
    ConversationDetailResult validateConversationOwnership(ConversationOwnershipQuery query);

    /**
     * 根据会话标识更新最后消息时间。
     */
    boolean touchConversation(ConversationTouchCommand command);

    /**
     * 仅在请求序号仍对应最新用户消息时更新当前题目。
     */
    boolean updateCurrentQuestionIfLatestUserMessageMatches(ConversationCurrentQuestionUpdateCommand command);

    /**
     * 仅在标题仍为默认值且请求序号匹配最新用户消息时回填标题。
     */
    boolean backfillTitleIfLatestUserMessageMatches(ConversationTitleBackfillCommand command);

    /**
     * 软删除当前登录用户自己的会话。
     */
    boolean deleteConversation(ConversationDeleteCommand command);

}
