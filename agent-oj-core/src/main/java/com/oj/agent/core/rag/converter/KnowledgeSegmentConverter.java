package com.oj.agent.core.rag.converter;

import com.oj.agent.common.constant.MetadataKeyConstant;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentResult;
import org.springframework.util.StringUtils;

import java.util.Map;

public final class KnowledgeSegmentConverter {

    private KnowledgeSegmentConverter() {
    }

    public static KnowledgeSegmentResult toResult(KnowledgeSegment entity) {
        if (entity == null) {
            return null;
        }
        KnowledgeSegmentResult result = new KnowledgeSegmentResult();
        result.setId(entity.getId());
        result.setText(entity.getText());
        result.setChunkId(entity.getChunkId());
        result.setParentChunkId(parseParentChunkId(entity.getMetadata()));
        result.setDocumentId(entity.getDocumentId());
        result.setChunkOrder(entity.getChunkOrder());
        return result;
    }

    static String parseParentChunkId(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return null;
        }
        try {
            Map<?, ?> metadata = JsonUtils.getObjectMapper().readValue(metadataJson, Map.class);
            Object parentChunkId = metadata.get(MetadataKeyConstant.PARENT_CHUNK_ID);
            if (parentChunkId == null) {
                return null;
            }
            String normalized = String.valueOf(parentChunkId).trim();
            return normalized.isEmpty() ? null : normalized;
        } catch (Exception exception) {
            return null;
        }
    }
}
