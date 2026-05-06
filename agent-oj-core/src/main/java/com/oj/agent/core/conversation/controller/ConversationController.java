package com.oj.agent.core.conversation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.common.api.Result;
import com.oj.agent.core.conversation.converter.ConversationConverter;
import com.oj.agent.core.conversation.model.request.ConversationCreateRequest;
import com.oj.agent.core.conversation.model.request.ConversationListRequest;
import com.oj.agent.core.conversation.model.request.ConversationMessageListRequest;
import com.oj.agent.core.conversation.model.response.ConversationCreateResponse;
import com.oj.agent.core.conversation.model.response.ConversationListItemResponse;
import com.oj.agent.core.conversation.model.response.ConversationMessageResponse;
import com.oj.agent.core.conversation.service.ConversationMessageService;
import com.oj.agent.core.conversation.service.ConversationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    // 会话服务。
    private final ConversationService conversationService;

    // 会话消息服务。
    private final ConversationMessageService conversationMessageService;

    public ConversationController(ConversationService conversationService,
                                  ConversationMessageService conversationMessageService) {
        this.conversationService = conversationService;
        this.conversationMessageService = conversationMessageService;
    }

    // 创建会话。
    @PostMapping
    public Result<ConversationCreateResponse> create(@RequestBody(required = false) ConversationCreateRequest request) {
        return Result.success(ConversationConverter.toCreateResponse(
                conversationService.createConversation(ConversationConverter.toCreateCommand(request))
        ));
    }

    // 分页查询会话列表。
    @GetMapping
    public Result<Page<ConversationListItemResponse>> list(ConversationListRequest request) {
        return Result.success(ConversationConverter.toListItemResponsePage(
                conversationService.listConversations(ConversationConverter.toListQuery(request))
        ));
    }

    // 分页查询会话消息列表。
    @GetMapping("/{conversationId}/messages")
    public Result<Page<ConversationMessageResponse>> listMessages(@PathVariable String conversationId,
                                                                  ConversationMessageListRequest request) {
        return Result.success(ConversationConverter.toMessageResponsePage(
                conversationMessageService.listMessages(
                        ConversationConverter.toMessageListQuery(conversationId, request)
                )
        ));
    }

    // 删除会话。
    @DeleteMapping("/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId) {
        conversationService.deleteConversation(ConversationConverter.toDeleteCommand(conversationId));
        return Result.success();
    }
}
