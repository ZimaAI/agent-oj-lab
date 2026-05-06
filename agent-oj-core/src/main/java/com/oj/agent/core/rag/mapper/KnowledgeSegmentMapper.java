package com.oj.agent.core.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeSegmentMapper extends BaseMapper<KnowledgeSegment> {
}
