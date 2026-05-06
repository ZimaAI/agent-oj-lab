package com.oj.agent.core.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.query.AlgorithmQuestionPageQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;

public interface AlgorithmQuestionService extends IService<AlgorithmQuestion> {

    /**
     * 分页查询算法题列表。
     */
    Page<AlgorithmQuestionResult> pageQuestions(AlgorithmQuestionPageQuery query);

    /**
     * 查询算法题详情。
     */
    AlgorithmQuestionResult getQuestion(AlgorithmQuestionGetQuery query);
}
