package com.oj.agent.core.question.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oj.agent.core.question.model.entity.AlgorithmCode;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;

import java.util.List;

public interface AlgorithmCodeService extends IService<AlgorithmCode> {

    List<AlgorithmCodeTemplateResult> listCodeTemplatesByQuestionId(Long questionId);

    AlgorithmCodeTemplateResult getCodeTemplateByQuestionIdAndLanguage(Long questionId, String language);

    List<AlgorithmCode> listByQuestionId(Long questionId);

    AlgorithmCode getByQuestionIdAndLanguage(Long questionId, String language);
}
