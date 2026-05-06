package com.oj.agent.core.question.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.mapper.AlgorithmQuestionMapper;
import com.oj.agent.core.question.mapper.AlgorithmQuestionTagMapper;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.model.entity.Tag;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.query.AlgorithmQuestionPageQuery;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.model.result.TagResult;
import com.oj.agent.core.question.service.AlgorithmCodeService;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlgorithmQuestionServiceImpl extends ServiceImpl<AlgorithmQuestionMapper, AlgorithmQuestion>
        implements AlgorithmQuestionService {

    private final AlgorithmQuestionTagMapper algorithmQuestionTagMapper;

    private final AlgorithmCodeService algorithmCodeService;

    public AlgorithmQuestionServiceImpl(AlgorithmQuestionTagMapper algorithmQuestionTagMapper,
                                        AlgorithmCodeService algorithmCodeService) {
        this.algorithmQuestionTagMapper = algorithmQuestionTagMapper;
        this.algorithmCodeService = algorithmCodeService;
    }

    @Override
    public Page<AlgorithmQuestionResult> pageQuestions(AlgorithmQuestionPageQuery query) {
        AlgorithmQuestionPageQuery safeQuery = query == null ? new AlgorithmQuestionPageQuery() : query;
        validatePageParams(safeQuery.getPageNum(), safeQuery.getPageSize());
        List<Long> questionIds = resolveQuestionIdsByTagIds(safeQuery.getTagIds());
        if (!CollectionUtils.isEmpty(safeQuery.getTagIds()) && CollectionUtils.isEmpty(questionIds)) {
            return new Page<>(safeQuery.getPageNum(), safeQuery.getPageSize(), 0);
        }

        Page<AlgorithmQuestion> page = new Page<>(safeQuery.getPageNum(), safeQuery.getPageSize());
        var questionPage = baseMapper.selectPageByCondition(
                page,
                safeQuery.getDifficulty(),
                safeQuery.getType(),
                safeQuery.getKeyword(),
                questionIds
        );

        long total = questionPage.getTotal();
        if (total <= 0) {
            total = baseMapper.countByCondition(
                    safeQuery.getDifficulty(),
                    safeQuery.getType(),
                    safeQuery.getKeyword(),
                    questionIds
            );
        }

        Page<AlgorithmQuestionResult> result = new Page<>(safeQuery.getPageNum(), safeQuery.getPageSize(), total);
        List<AlgorithmQuestion> pagedRecords = ensurePagedRecords(
                questionPage.getRecords(),
                safeQuery.getPageNum(),
                safeQuery.getPageSize()
        );
        result.setRecords(fillTags(pagedRecords).stream().map(this::toPageQuestionResult).toList());
        return result;
    }

    @Override
    public AlgorithmQuestionResult getQuestion(AlgorithmQuestionGetQuery query) {
        Long id = query == null ? null : query.getId();
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("question id is invalid");
        }
        AlgorithmQuestion question = baseMapper.selectById(id);
        if (question == null || Integer.valueOf(1).equals(question.getIsDelete())) {
            throw new IllegalArgumentException("algorithm question does not exist");
        }

        List<TagResult> tags = buildTagResultList(algorithmQuestionTagMapper.selectTagsByQuestionId(id));
        List<AlgorithmCodeTemplateResult> codeTemplates = algorithmCodeService.listCodeTemplatesByQuestionId(id);
        return AlgorithmQuestionConverter.toDetailQuestionResult(question, tags, codeTemplates);
    }

    private List<Long> resolveQuestionIdsByTagIds(List<Long> tagIds) {
        if (CollectionUtils.isEmpty(tagIds)) {
            return null;
        }
        return algorithmQuestionTagMapper.selectQuestionIdsByTagIds(tagIds, tagIds.size());
    }

    private List<QuestionWithTags> fillTags(List<AlgorithmQuestion> questions) {
        if (CollectionUtils.isEmpty(questions)) {
            return Collections.emptyList();
        }
        List<Long> questionIds = questions.stream().map(AlgorithmQuestion::getId).toList();
        Map<Long, List<TagResult>> tagMap = buildTagMap(questionIds);
        return questions.stream()
                .map(question -> new QuestionWithTags(question, tagMap.getOrDefault(question.getId(), Collections.emptyList())))
                .toList();
    }

    // Fallback protection in case SQL pagination interceptor is bypassed.
    private List<AlgorithmQuestion> ensurePagedRecords(List<AlgorithmQuestion> records, long pageNum, long pageSize) {
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        if (records.size() <= pageSize) {
            return records;
        }
        long startIndexLong = (pageNum - 1) * pageSize;
        if (startIndexLong >= records.size()) {
            return Collections.emptyList();
        }
        int startIndex = (int) startIndexLong;
        int endIndex = (int) Math.min(startIndexLong + pageSize, records.size());
        return records.subList(startIndex, endIndex);
    }

    private Map<Long, List<TagResult>> buildTagMap(List<Long> questionIds) {
        Map<Long, List<TagResult>> result = new LinkedHashMap<>();
        questionIds.forEach(questionId -> result.put(questionId, new ArrayList<>()));
        List<Map<String, Object>> questionTagPairs = algorithmQuestionTagMapper.selectQuestionTagPairsByQuestionIds(questionIds);
        questionTagPairs.forEach(pair -> {
            Long questionId = ((Number) pair.get("questionId")).longValue();
            Tag tag = new Tag();
            tag.setId(((Number) pair.get("id")).longValue());
            tag.setTagName((String) pair.get("tagName"));
            tag.setTagType((String) pair.get("tagType"));
            tag.setDescription((String) pair.get("description"));
            tag.setCreateTime((java.time.LocalDateTime) pair.get("createTime"));
            tag.setUpdateTime((java.time.LocalDateTime) pair.get("updateTime"));
            List<TagResult> tagResults = result.get(questionId);
            if (tagResults != null) {
                tagResults.add(AlgorithmQuestionConverter.toTagResult(tag));
            }
        });
        return result;
    }

    private AlgorithmQuestionResult toPageQuestionResult(QuestionWithTags questionWithTags) {
        return AlgorithmQuestionConverter.toPageQuestionResult(questionWithTags.question(), questionWithTags.tags());
    }

    private List<TagResult> buildTagResultList(List<Tag> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return Collections.emptyList();
        }
        return tags.stream().map(AlgorithmQuestionConverter::toTagResult).toList();
    }

    private void validatePageParams(long current, long pageSize) {
        if (current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("invalid page parameters");
        }
    }

    private record QuestionWithTags(AlgorithmQuestion question, List<TagResult> tags) {
    }
}
