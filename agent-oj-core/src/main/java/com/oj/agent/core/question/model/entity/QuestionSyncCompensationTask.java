package com.oj.agent.core.question.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_sync_compensation_task")
public class QuestionSyncCompensationTask {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 题目 ID。
     */
    private Long questionId;

    /**
     * 操作类型。
     */
    private String operationType;

    /**
     * 状态：PENDING/PROCESSING/SUCCESS/FAILED。
     */
    private String status;

    /**
     * 最近错误信息。
     */
    private String errorMessage;

    /**
     * 重试次数。
     */
    private Integer retryCount;

    /**
     * 最后尝试时间。
     */
    private LocalDateTime lastAttemptTime;

    /**
     * 是否删除。
     */
    private Integer isDelete;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
