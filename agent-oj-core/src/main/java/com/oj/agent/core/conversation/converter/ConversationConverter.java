package com.oj.agent.core.conversation.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.conversation.model.command.ConversationDeleteCommand;
import com.oj.agent.core.conversation.model.command.ConversationCreateCommand;
import com.oj.agent.core.conversation.model.command.ConversationMessageCreateCommand;
import com.oj.agent.core.conversation.model.entity.Conversation;
import com.oj.agent.core.conversation.model.entity.ConversationMessage;
import com.oj.agent.core.conversation.model.query.ConversationListQuery;
import com.oj.agent.core.conversation.model.query.ConversationMessageListQuery;
import com.oj.agent.core.conversation.model.request.ConversationListRequest;
import com.oj.agent.core.conversation.model.request.ConversationCreateRequest;
import com.oj.agent.core.conversation.model.result.ConversationCreateResult;
import com.oj.agent.core.conversation.model.result.ConversationDetailResult;
import com.oj.agent.core.conversation.model.result.ConversationListItemResult;
import com.oj.agent.core.conversation.model.result.ConversationMessageCreateResult;
import com.oj.agent.core.conversation.model.result.ConversationMessageResult;
import com.oj.agent.core.conversation.model.request.ConversationMessageListRequest;
import com.oj.agent.core.conversation.model.response.ConversationCreateResponse;
import com.oj.agent.core.conversation.model.response.ConversationListItemResponse;
import com.oj.agent.core.conversation.model.response.ConversationMessageResponse;

import java.time.LocalDateTime;

public final class ConversationConverter {

    private ConversationConverter() {
    }

    public static ConversationCreateCommand toCreateCommand(ConversationCreateRequest request) {
        return buildCreateCommand(request == null ? null : request.getCurrentQuestionId());
    }

    public static ConversationDeleteCommand toDeleteCommand(String conversationId) {
        ConversationDeleteCommand command = new ConversationDeleteCommand();
        command.setConversationId(conversationId);
        return command;
    }

    private static ConversationCreateCommand buildCreateCommand(Long currentQuestionId) {
        ConversationCreateCommand command = new ConversationCreateCommand();
        command.setCurrentQuestionId(currentQuestionId);
        return command;
    }

    public static ConversationMessageListQuery toMessageListQuery(String conversationId,
                                                                  ConversationMessageListRequest request) {
        ConversationMessageListQuery query = new ConversationMessageListQuery();
        query.setConversationId(conversationId);
        if (request != null) {
            query.setCurrent(request.getCurrent());
            query.setPageSize(request.getPageSize());
        }
        return query;
    }

    public static ConversationListQuery toListQuery(ConversationListRequest request) {
        ConversationListQuery query = new ConversationListQuery();
        if (request != null) {
            query.setCurrent(request.getCurrent());
            query.setPageSize(request.getPageSize());
        }
        return query;
    }

    public static ConversationMessage toMessageEntity(ConversationMessageCreateCommand command) {
        if (command == null) {
            return null;
        }
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(command.getConversationId());
        message.setSender(command.getSender());
        message.setMessageType(command.getMessageType());
        message.setContent(command.getContent());
        message.setQuestionId(command.getQuestionId());
        message.setSubmissionId(command.getSubmissionId());
        message.setEvaluationId(command.getEvaluationId());
        message.setTraceId(command.getTraceId());
        message.setResultType(command.getResultType());
        message.setResultData(command.getResultData());
        return message;
    }

    public static Conversation toConversationEntity(Long userId,
                                                    String conversationId,
                                                    String title,
                                                    String sessionStatus,
                                                    ConversationCreateCommand command,
                                                    LocalDateTime now) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setConversationId(conversationId);
        conversation.setTitle(title);
        conversation.setCurrentQuestionId(command == null ? null : command.getCurrentQuestionId());
        conversation.setSessionStatus(sessionStatus);
        conversation.setLastMessageTime(now);
        conversation.setIsDelete(0);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        return conversation;
    }

    public static ConversationCreateResult toCreateResult(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        ConversationCreateResult result = new ConversationCreateResult();
        result.setConversationId(conversation.getConversationId());
        result.setTitle(conversation.getTitle());
        result.setLastMessageTime(conversation.getLastMessageTime());
        result.setSessionStatus(conversation.getSessionStatus());
        result.setCurrentQuestionId(conversation.getCurrentQuestionId());
        result.setCreateTime(conversation.getCreateTime());
        return result;
    }

    public static ConversationCreateResponse toCreateResponse(ConversationCreateResult result) {
        if (result == null) {
            return null;
        }
        ConversationCreateResponse response = new ConversationCreateResponse();
        response.setConversationId(result.getConversationId());
        response.setTitle(result.getTitle());
        response.setLastMessageTime(result.getLastMessageTime());
        response.setSessionStatus(result.getSessionStatus());
        response.setCurrentQuestionId(result.getCurrentQuestionId());
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static ConversationMessageCreateResult toMessageCreateResult(ConversationMessage message) {
        if (message == null) {
            return null;
        }
        ConversationMessageCreateResult result = new ConversationMessageCreateResult();
        result.setId(message.getId());
        result.setConversationId(message.getConversationId());
        result.setSequenceNo(message.getSequenceNo());
        result.setCreateTime(message.getCreateTime());
        return result;
    }

    public static ConversationMessageResult toMessageResult(ConversationMessage message, String resultSummary) {
        if (message == null) {
            return null;
        }
        ConversationMessageResult result = new ConversationMessageResult();
        result.setSender(message.getSender());
        result.setContent(message.getContent());
        result.setMessageType(message.getMessageType());
        result.setSequenceNo(message.getSequenceNo());
        result.setCreateTime(message.getCreateTime());
        result.setResultType(message.getResultType());
        result.setResultSummary(resultSummary);
        return result;
    }

    public static ConversationMessageResponse toMessageResponse(ConversationMessageResult result) {
        if (result == null) {
            return null;
        }
        ConversationMessageResponse response = new ConversationMessageResponse();
        response.setSender(result.getSender());
        response.setContent(result.getContent());
        response.setMessageType(result.getMessageType());
        response.setSequenceNo(result.getSequenceNo());
        response.setCreateTime(result.getCreateTime());
        response.setResultType(result.getResultType());
        response.setResultSummary(result.getResultSummary());
        return response;
    }

    public static ConversationListItemResult toListItemResult(Conversation conversation, String latestMessagePreview) {
        if (conversation == null) {
            return null;
        }
        ConversationListItemResult result = new ConversationListItemResult();
        result.setConversationId(conversation.getConversationId());
        result.setTitle(conversation.getTitle());
        result.setLastMessageTime(conversation.getLastMessageTime());
        result.setSessionStatus(conversation.getSessionStatus());
        result.setCurrentQuestionId(conversation.getCurrentQuestionId());
        result.setLatestMessagePreview(latestMessagePreview);
        result.setCreateTime(conversation.getCreateTime());
        return result;
    }

    public static ConversationListItemResponse toListItemResponse(ConversationListItemResult result) {
        if (result == null) {
            return null;
        }
        ConversationListItemResponse response = new ConversationListItemResponse();
        response.setConversationId(result.getConversationId());
        response.setTitle(result.getTitle());
        response.setLastMessageTime(result.getLastMessageTime());
        response.setSessionStatus(result.getSessionStatus());
        response.setCurrentQuestionId(result.getCurrentQuestionId());
        response.setLatestMessagePreview(result.getLatestMessagePreview());
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static Page<ConversationListItemResponse> toListItemResponsePage(Page<ConversationListItemResult> resultPage) {
        Page<ConversationListItemResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(ConversationConverter::toListItemResponse)
                .toList());
        return responsePage;
    }

    public static Page<ConversationMessageResponse> toMessageResponsePage(Page<ConversationMessageResult> resultPage) {
        Page<ConversationMessageResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(ConversationConverter::toMessageResponse)
                .toList());
        return responsePage;
    }

    public static ConversationDetailResult toDetailResult(Conversation conversation, String latestMessagePreview) {
        if (conversation == null) {
            return null;
        }
        ConversationDetailResult result = new ConversationDetailResult();
        result.setConversationId(conversation.getConversationId());
        result.setTitle(conversation.getTitle());
        result.setCurrentQuestionId(conversation.getCurrentQuestionId());
        result.setSessionStatus(conversation.getSessionStatus());
        result.setLastMessageTime(conversation.getLastMessageTime());
        result.setCreateTime(conversation.getCreateTime());
        result.setLatestMessagePreview(latestMessagePreview);
        return result;
    }
}
