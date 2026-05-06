package com.oj.agent.core.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.core.file.model.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {
}
