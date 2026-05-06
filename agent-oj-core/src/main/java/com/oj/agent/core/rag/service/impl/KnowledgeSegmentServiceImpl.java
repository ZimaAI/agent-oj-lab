package com.oj.agent.core.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.core.rag.converter.KnowledgeSegmentConverter;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentListQuery;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentResult;
import com.oj.agent.core.rag.service.KnowledgeSegmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class KnowledgeSegmentServiceImpl implements KnowledgeSegmentService {

    private final KnowledgeSegmentMapper knowledgeSegmentMapper;

    public KnowledgeSegmentServiceImpl(KnowledgeSegmentMapper knowledgeSegmentMapper) {
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSegmentResult> listSegments(KnowledgeSegmentListQuery query) {
        if (query == null) {
            return List.of();
        }

        List<Long> segmentIds = normalizeSegmentIds(query.getSegmentIds());
        List<String> chunkIds = normalizeChunkIds(query.getChunkIds());
        boolean hasSegmentIds = !segmentIds.isEmpty();
        boolean hasChunkIds = !chunkIds.isEmpty();
        if (!hasSegmentIds && !hasChunkIds) {
            return List.of();
        }

        LambdaQueryWrapper<KnowledgeSegment> queryWrapper = new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDeleted, 0)
                .and(wrapper -> {
                    if (hasSegmentIds) {
                        wrapper.in(KnowledgeSegment::getId, segmentIds);
                    }
                    if (hasChunkIds) {
                        if (hasSegmentIds) {
                            wrapper.or();
                        }
                        wrapper.in(KnowledgeSegment::getChunkId, chunkIds);
                    }
                });

        return knowledgeSegmentMapper.selectList(queryWrapper).stream()
                .filter(Objects::nonNull)
                .filter(entity -> Objects.equals(entity.getDeleted(), 0))
                .map(KnowledgeSegmentConverter::toResult)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> normalizeSegmentIds(List<Long> segmentIds) {
        if (segmentIds == null || segmentIds.isEmpty()) {
            return List.of();
        }
        return segmentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<String> normalizeChunkIds(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String chunkId : chunkIds) {
            if (!StringUtils.hasText(chunkId)) {
                continue;
            }
            normalized.add(chunkId.trim());
        }
        return List.copyOf(normalized);
    }
}
