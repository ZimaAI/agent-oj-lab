package com.oj.agent.core.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.core.question.model.entity.QuestionSyncCompensationTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目补偿任务 Mapper。
 */
@Mapper
public interface QuestionSyncCompensationTaskMapper extends BaseMapper<QuestionSyncCompensationTask> {
}
