package com.oj.agent.core.file.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_record")
public class FileRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String bucketName;

    private String objectName;

    private String originalFilename;

    private Long fileSize;

    private String contentType;

    private String fileUrl;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
