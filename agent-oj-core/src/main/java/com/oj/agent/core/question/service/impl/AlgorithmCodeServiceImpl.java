package com.oj.agent.core.question.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.mapper.AlgorithmCodeMapper;
import com.oj.agent.core.question.model.entity.AlgorithmCode;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;
import com.oj.agent.core.question.service.AlgorithmCodeService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Service
public class AlgorithmCodeServiceImpl extends ServiceImpl<AlgorithmCodeMapper, AlgorithmCode>
        implements AlgorithmCodeService {

    @Override
    public List<AlgorithmCodeTemplateResult> listCodeTemplatesByQuestionId(Long questionId) {
        return listByQuestionId(questionId).stream()
                .map(AlgorithmQuestionConverter::toCodeTemplateResult)
                .toList();
    }

    @Override
    public AlgorithmCodeTemplateResult getCodeTemplateByQuestionIdAndLanguage(Long questionId, String language) {
        return AlgorithmQuestionConverter.toCodeTemplateResult(getByQuestionIdAndLanguage(questionId, language));
    }

    @Override
    public List<AlgorithmCode> listByQuestionId(Long questionId) {
        if (questionId == null || questionId <= 0) {
            return Collections.emptyList();
        }
        List<AlgorithmCode> codes = baseMapper.selectByQuestionId(questionId);
        if (CollectionUtils.isEmpty(codes)) {
            return Collections.emptyList();
        }
        return codes;
    }

    @Override
    public AlgorithmCode getByQuestionIdAndLanguage(Long questionId, String language) {
        if (questionId == null || questionId <= 0 || !org.springframework.util.StringUtils.hasText(language)) {
            return null;
        }
        return baseMapper.selectByQuestionIdAndLanguage(questionId, language.trim().toUpperCase());
    }
}
