package com.oj.agent.core.conversation.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation_message")
public class ConversationMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String sender;

    private String messageType;

    private String content;

    private Long questionId;

    private Long submissionId;

    private Long evaluationId;

    private String traceId;

    private Integer sequenceNo;

    private String resultType;

    private String resultData;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
