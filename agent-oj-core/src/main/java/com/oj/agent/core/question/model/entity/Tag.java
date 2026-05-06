package com.oj.agent.core.question.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tag")
public class Tag {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String tagName;

    private String tagType;

    private String description;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
