package com.oj.agent.core.rag.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.oj.agent.core.rag.exception.VectorStoreException;
import com.oj.agent.core.rag.model.VectorDocument;
import com.oj.agent.core.rag.model.VectorSearchHit;
import com.oj.agent.core.rag.model.VectorSearchRequest;
import com.oj.agent.core.rag.model.VectorSearchResult;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class MilvusKnowledgeSegmentVectorStore implements AgentVectorStore {

    private static final String FIELD_ID = "id";
    private static final String FIELD_SEGMENT_ID = "segment_id";
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_IS_DELETE = "is_delete";
    private static final int DEFAULT_SEARCH_TOP_K = 20;

    private final MilvusClientV2 milvusClient;
    private final String collectionName;
    private final String embeddingField;
    private final IndexParam.MetricType metricType;

    public MilvusKnowledgeSegmentVectorStore(MilvusClientV2 milvusClient,
                                             String collectionName,
                                             String embeddingField,
                                             IndexParam.MetricType metricType) {
        this.milvusClient = Objects.requireNonNull(milvusClient, "milvusClient must not be null");
        this.collectionName = requireText(collectionName, "collectionName must not be blank");
        this.embeddingField = requireText(embeddingField, "embeddingField must not be blank");
        this.metricType = metricType == null ? IndexParam.MetricType.COSINE : metricType;
    }

    @Override
    public void upsert(VectorDocument document, float[] embedding) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        Long segmentId = resolveSegmentId(document);
        if (segmentId == null) {
            throw new IllegalArgumentException("document.segmentId must not be null");
        }
        try {
            JsonObject entity = buildMilvusEntity(document, segmentId, embedding);
            milvusClient.upsert(UpsertReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(entity))
                    .build());
        } catch (VectorStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Upsert knowledge segment vector failed", exception);
        }
    }

    @Override
    public boolean markDeleted(Long segmentId, String reason) {
        if (segmentId == null) {
            throw new IllegalArgumentException("segmentId must not be null");
        }
        try {
            DeleteResp response = milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(segmentIdFilter(segmentId))
                    .build());
            return response != null && response.getDeleteCnt() > 0;
        } catch (VectorStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Mark knowledge segment deleted failed", exception);
        }
    }

    @Override
    public int deleteByDocumentId(Long documentId, String reason) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        try {
            DeleteResp response = milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(documentIdFilter(documentId))
                    .build());
            long deletedCount = response == null ? 0L : Math.max(response.getDeleteCnt(), 0L);
            return deletedCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) deletedCount;
        } catch (VectorStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Delete knowledge segment vectors by documentId failed", exception);
        }
    }

    @Override
    public Optional<VectorDocument> findByQuestionId(Long segmentId) {
        if (segmentId == null) {
            return Optional.empty();
        }
        try {
            QueryResp response = milvusClient.query(QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(segmentIdFilter(segmentId))
                    .limit(1)
                    .outputFields(queryOutputFields())
                    .build());
            List<QueryResp.QueryResult> results = response == null ? List.of() : response.getQueryResults();
            if (results == null || results.isEmpty()) {
                return Optional.empty();
            }
            VectorDocument document = toVectorDocument(results.get(0).getEntity(), segmentId);
            return document == null ? Optional.empty() : Optional.of(document);
        } catch (VectorStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Find knowledge segment by id failed", exception);
        }
    }

    @Override
    public VectorSearchResult search(VectorSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getQueryVector() == null || request.getQueryVector().isEmpty()) {
            return VectorSearchResult.builder().hits(List.of()).build();
        }
        try {
            int topK = request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : DEFAULT_SEARCH_TOP_K;
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(embeddingField)
                    .metricType(metricType)
                    .topK(topK)
                    .outputFields(searchOutputFields())
                    .data(List.of(new FloatVec(request.getQueryVector())))
                    .build();
            SearchResp response = milvusClient.search(searchReq);
            List<SearchResp.SearchResult> rawHits = unwrapRawHits(response);
            Stream<VectorSearchHit> stream = rawHits.stream()
                    .map(this::toSearchHit)
                    .filter(Objects::nonNull)
                    .filter(hit -> score(hit.getFinalScore()) > score(request.getScoreThreshold()))
                    .sorted(Comparator.comparingDouble((VectorSearchHit hit) -> score(hit.getFinalScore())).reversed());
            if (request.getTopK() != null && request.getTopK() > 0) {
                stream = stream.limit(request.getTopK());
            }
            return VectorSearchResult.builder().hits(stream.toList()).build();
        } catch (VectorStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Search knowledge segment vectors failed", exception);
        }
    }

    @Override
    public int clearQuestionVectors() {
        try {
            DeleteResp response = milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(FIELD_SEGMENT_ID + " >= 0")
                    .build());
            long deletedCount = response == null ? 0L : Math.max(response.getDeleteCnt(), 0L);
            return deletedCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) deletedCount;
        } catch (VectorStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Clear knowledge segment vectors failed", exception);
        }
    }

    private JsonObject buildMilvusEntity(VectorDocument document, Long segmentId, float[] embedding) {
        JsonObject entity = new JsonObject();
        String id = isBlank(document.getId()) ? String.valueOf(segmentId) : document.getId().trim();
        entity.addProperty(FIELD_ID, id);
        entity.addProperty(FIELD_SEGMENT_ID, segmentId);
        if (document.getDocumentId() != null) {
            entity.addProperty(FIELD_DOCUMENT_ID, document.getDocumentId());
        }
        if (!isBlank(document.getChunkId())) {
            entity.addProperty(FIELD_CHUNK_ID, document.getChunkId().trim());
        }
        if (!isBlank(document.getTitle())) {
            entity.addProperty(FIELD_TITLE, document.getTitle().trim());
        }
        if (!isBlank(document.getContent())) {
            entity.addProperty(FIELD_CONTENT, document.getContent().trim());
        }
        entity.addProperty(FIELD_IS_DELETE, document.isDeleted() ? 1 : 0);
        JsonArray vectorValues = new JsonArray();
        for (float value : embedding) {
            vectorValues.add(value);
        }
        entity.add(embeddingField, vectorValues);
        return entity;
    }

    private List<String> queryOutputFields() {
        return List.of(
                FIELD_ID,
                FIELD_SEGMENT_ID,
                FIELD_DOCUMENT_ID,
                FIELD_CHUNK_ID,
                FIELD_TITLE,
                FIELD_CONTENT,
                FIELD_IS_DELETE,
                embeddingField
        );
    }

    private List<String> searchOutputFields() {
        return List.of(
                FIELD_ID,
                FIELD_SEGMENT_ID,
                FIELD_DOCUMENT_ID,
                FIELD_CHUNK_ID,
                FIELD_TITLE,
                FIELD_CONTENT,
                FIELD_IS_DELETE
        );
    }

    private String segmentIdFilter(Long segmentId) {
        return FIELD_SEGMENT_ID + " == " + segmentId;
    }

    private String documentIdFilter(Long documentId) {
        return FIELD_DOCUMENT_ID + " == " + documentId;
    }

    private List<SearchResp.SearchResult> unwrapRawHits(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        List<SearchResp.SearchResult> firstQueryResults = response.getSearchResults().get(0);
        return firstQueryResults == null ? List.of() : firstQueryResults;
    }

    private VectorSearchHit toSearchHit(SearchResp.SearchResult result) {
        if (result == null || result.getEntity() == null) {
            return null;
        }
        Map<String, Object> entity = result.getEntity();
        if (isDeleted(entity.get(FIELD_IS_DELETE))) {
            return null;
        }
        Long segmentId = toLong(entity.get(FIELD_SEGMENT_ID));
        if (segmentId == null) {
            segmentId = toLong(result.getId());
        }
        if (segmentId == null) {
            return null;
        }
        double similarity = toSimilarityScore(result.getScore());
        return VectorSearchHit.builder()
                .documentId(toText(entity.get(FIELD_ID)))
                .questionId(segmentId)
                .title(toText(entity.get(FIELD_TITLE)))
                .similarity(similarity)
                .finalScore(similarity)
                .build();
    }

    private VectorDocument toVectorDocument(Map<String, Object> entity, Long fallbackSegmentId) {
        if (entity == null || entity.isEmpty()) {
            return null;
        }
        Long segmentId = toLong(entity.get(FIELD_SEGMENT_ID));
        if (segmentId == null) {
            segmentId = fallbackSegmentId;
        }
        if (segmentId == null) {
            return null;
        }
        return VectorDocument.builder()
                .id(toText(entity.get(FIELD_ID)))
                .questionId(segmentId)
                .segmentId(segmentId)
                .documentId(toLong(entity.get(FIELD_DOCUMENT_ID)))
                .chunkId(toText(entity.get(FIELD_CHUNK_ID)))
                .title(toText(entity.get(FIELD_TITLE)))
                .content(toText(entity.get(FIELD_CONTENT)))
                .embedding(toFloatList(entity.get(embeddingField)))
                .deleted(isDeleted(entity.get(FIELD_IS_DELETE)))
                .build();
    }

    private Long resolveSegmentId(VectorDocument document) {
        if (document.getSegmentId() != null) {
            return document.getSegmentId();
        }
        return document.getQuestionId();
    }

    private double score(Double value) {
        return value == null ? 0D : value;
    }

    private Double toSimilarityScore(Float rawScore) {
        if (rawScore == null) {
            return 0D;
        }
        if (metricType == IndexParam.MetricType.L2
                || metricType == IndexParam.MetricType.HAMMING
                || metricType == IndexParam.MetricType.JACCARD) {
            return 1D / (1D + Math.max(rawScore, 0F));
        }
        return (double) rawScore;
    }

    private List<Float> toFloatList(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        List<Float> floatValues = new ArrayList<>(values.size());
        for (Object item : values) {
            Float parsed = toFloat(item);
            if (parsed != null) {
                floatValues.add(parsed);
            }
        }
        return floatValues;
    }

    private Float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return Float.parseFloat(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isDeleted(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            return "1".equals(normalized) || "true".equalsIgnoreCase(normalized);
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String requireText(String value, String errorMessage) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }
}
