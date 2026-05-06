package com.oj.agent.core.question.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("algorithm_question_knowledge_document")
public class AlgorithmQuestionKnowledgeDocument {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long docId;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
