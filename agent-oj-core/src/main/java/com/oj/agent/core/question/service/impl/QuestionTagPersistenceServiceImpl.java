package com.oj.agent.core.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.core.question.mapper.AlgorithmQuestionTagMapper;
import com.oj.agent.core.question.mapper.TagMapper;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionTag;
import com.oj.agent.core.question.model.entity.Tag;
import com.oj.agent.core.question.service.QuestionTagPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionTagPersistenceServiceImpl implements QuestionTagPersistenceService {

    private static final String DEFAULT_TAG_TYPE = "TOPIC";

    private final TagMapper tagMapper;
    private final AlgorithmQuestionTagMapper algorithmQuestionTagMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistQuestionTags(Long questionId, List<String> rawTagNames) {
        if (questionId == null || questionId <= 0) {
            throw new IllegalArgumentException("questionId 非法");
        }
        List<String> normalizedTagNames = normalizeTagNames(rawTagNames);
        if (normalizedTagNames.isEmpty()) {
            return;
        }

        Map<String, Tag> tagByName = new LinkedHashMap<>();
        List<Tag> existingTags = tagMapper.selectByTagNames(normalizedTagNames);
        existingTags.forEach(tag -> tagByName.put(tag.getTagName(), tag));

        for (String tagName : normalizedTagNames) {
            if (tagByName.containsKey(tagName)) {
                continue;
            }
            Tag insertedTag = insertOrGetTag(tagName);
            tagByName.put(tagName, insertedTag);
        }

        for (String tagName : normalizedTagNames) {
            Tag tag = tagByName.get(tagName);
            if (tag == null || tag.getId() == null) {
                throw new IllegalStateException("标签持久化失败: " + tagName);
            }
            saveQuestionTagRelation(questionId, tag.getId());
        }
    }

    private List<String> normalizeTagNames(List<String> rawTagNames) {
        if (CollectionUtils.isEmpty(rawTagNames)) {
            return List.of();
        }
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String rawTagName : rawTagNames) {
            if (rawTagName == null) {
                continue;
            }
            String normalized = rawTagName.trim();
            if (!normalized.isEmpty()) {
                deduplicated.add(normalized);
            }
        }
        return deduplicated.stream().toList();
    }

    private Tag insertOrGetTag(String tagName) {
        Tag tag = new Tag();
        tag.setTagName(tagName);
        tag.setTagType(DEFAULT_TAG_TYPE);
        tag.setIsDelete(0);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException ignored) {
            return getTagByName(tagName);
        }
    }

    private Tag getTagByName(String tagName) {
        return tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getTagName, tagName)
                .eq(Tag::getIsDelete, 0)
                .last("LIMIT 1"));
    }

    private void saveQuestionTagRelation(Long questionId, Long tagId) {
        AlgorithmQuestionTag relation = new AlgorithmQuestionTag();
        relation.setQuestionId(questionId);
        relation.setTagId(tagId);
        try {
            algorithmQuestionTagMapper.insert(relation);
        } catch (DuplicateKeyException ignored) {
            // 关系已存在时忽略，保证幂等写入。
        }
    }
}
