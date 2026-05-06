package com.oj.agent.core.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("algorithm_question_vector")
public class AlgorithmQuestionVector {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private String title;

    private String difficulty;

    private String language;

    private String tagsJson;

    private String embeddingText;

    private String embedding;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String syncStatus;

    private String syncErrorMessage;
}
