package com.oj.agent.core.conversation.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String conversationId;

    private String title;

    private Long currentQuestionId;

    private String sessionStatus;

    private LocalDateTime lastMessageTime;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
