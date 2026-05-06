package com.oj.agent.core.question.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("algorithm_question_tag")
public class AlgorithmQuestionTag {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long tagId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
