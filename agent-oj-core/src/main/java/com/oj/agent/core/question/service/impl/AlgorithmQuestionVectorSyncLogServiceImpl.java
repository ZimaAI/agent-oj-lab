package com.oj.agent.core.question.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.question.mapper.AlgorithmQuestionVectorSyncLogMapper;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionVectorSyncLog;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncLogService;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmQuestionVectorSyncLogServiceImpl
        extends ServiceImpl<AlgorithmQuestionVectorSyncLogMapper, AlgorithmQuestionVectorSyncLog>
        implements AlgorithmQuestionVectorSyncLogService {
}
