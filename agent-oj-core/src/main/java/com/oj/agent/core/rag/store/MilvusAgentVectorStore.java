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

public class MilvusAgentVectorStore implements AgentVectorStore {

    private static final String FIELD_ID = "id";
    private static final String FIELD_QUESTION_ID = "question_id";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_DIFFICULTY = "difficulty";
    private static final String FIELD_LANGUAGE = "language";
    private static final String FIELD_IS_DELETE = "is_delete";
    private static final String LANGUAGE_SHARED = "SHARED";
    private static final int DEFAULT_SEARCH_TOP_K = 20;

    private final MilvusClientV2 milvusClient;
    private final String collectionName;
    private final String embeddingField;
    private final IndexParam.MetricType metricType;

    public MilvusAgentVectorStore(MilvusClientV2 milvusClient,
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
        if (embedding == null) {
            throw new IllegalArgumentException("embedding must not be null");
        }
        if (embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        if (document.getQuestionId() == null) {
            throw new IllegalArgumentException("document.questionId must not be null");
        }
        try {
            JsonObject entity = buildMilvusEntity(document, embedding);
            milvusClient.upsert(UpsertReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(entity))
                    .build());
        } catch (VectorStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new VectorStoreException("Upsert failed", e);
        }
    }

    @Override
    public boolean markDeleted(Long questionId, String reason) {
        if (questionId == null) {
            throw new IllegalArgumentException("questionId must not be null");
        }
        try {
            DeleteResp response = milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(questionIdFilter(questionId))
                    .build());
            return response != null && response.getDeleteCnt() > 0;
        } catch (VectorStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new VectorStoreException("Mark deleted failed", e);
        }
    }

    @Override
    public Optional<VectorDocument> findByQuestionId(Long questionId) {
        if (questionId == null) {
            return Optional.empty();
        }
        try {
            QueryResp response = milvusClient.query(QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(questionIdFilter(questionId))
                    .limit(1)
                    .outputFields(queryOutputFields())
                    .build());
            List<QueryResp.QueryResult> results = response == null ? List.of() : response.getQueryResults();
            if (results == null || results.isEmpty()) {
                return Optional.empty();
            }
            VectorDocument document = toVectorDocument(results.get(0).getEntity(), questionId);
            return document == null ? Optional.empty() : Optional.of(document);
        } catch (VectorStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new VectorStoreException("Find by questionId failed", e);
        }
    }

    @Override
    public VectorSearchResult search(VectorSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.getQueryVector() == null || request.getQueryVector().isEmpty()) {
            return VectorSearchResult.builder().hits(List.of()).build();
        }
        try {
            int requestedTopK = request.getTopK() != null && request.getTopK() > 0
                    ? request.getTopK()
                    : DEFAULT_SEARCH_TOP_K;
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(embeddingField)
                    .metricType(metricType)
                    .topK(requestedTopK)
                    .outputFields(searchOutputFields())
                    .data(List.of(new FloatVec(request.getQueryVector())))
                    .build();
            SearchResp response = milvusClient.search(searchReq);
            List<SearchResp.SearchResult> rawHits = unwrapRawHits(response);

            Stream<VectorSearchHit> processedStream = rawHits.stream()
                    .map(this::toSearchHit)
                    .filter(Objects::nonNull)
                    .filter(hit -> matchesFilter(request.getDifficultyFilter(), hit.getDifficulty()))
                    .filter(hit -> matchesLanguageFilter(request.getLanguageFilter(), hit.getLanguage()))
                    .map(hit -> withFinalScore(hit, computeFinalScore(hit, request)))
                    .filter(hit -> score(hit.getFinalScore()) > score(request.getScoreThreshold()))
                    .sorted(Comparator.comparingDouble((VectorSearchHit hit) -> score(hit.getFinalScore())).reversed()
                            .thenComparing(Comparator.comparingDouble((VectorSearchHit hit) -> score(hit.getSimilarity())).reversed())
                            .thenComparing(hit -> hit.getQuestionId() == null ? Long.MAX_VALUE : hit.getQuestionId()));

            if (request.getTopK() != null && request.getTopK() > 0) {
                processedStream = processedStream.limit(request.getTopK());
            }
            return VectorSearchResult.builder().hits(processedStream.toList()).build();
        } catch (VectorStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new VectorStoreException("Search failed", e);
        }
    }

    @Override
    public int clearQuestionVectors() {
        try {
            DeleteResp response = milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(FIELD_QUESTION_ID + " >= 0")
                    .build());
            long deletedCount = response == null ? 0L : Math.max(response.getDeleteCnt(), 0L);
            return deletedCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) deletedCount;
        } catch (VectorStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new VectorStoreException("Clear question vectors failed", e);
        }
    }

    private JsonObject buildMilvusEntity(VectorDocument document, float[] embedding) {
        JsonObject entity = new JsonObject();
        String documentId = isBlank(document.getId())
                ? String.valueOf(document.getQuestionId())
                : document.getId().trim();
        entity.addProperty(FIELD_ID, documentId);
        entity.addProperty(FIELD_QUESTION_ID, document.getQuestionId());
        if (!isBlank(document.getTitle())) {
            entity.addProperty(FIELD_TITLE, document.getTitle().trim());
        }
        if (!isBlank(document.getDifficulty())) {
            entity.addProperty(FIELD_DIFFICULTY, document.getDifficulty().trim());
        }
        entity.addProperty(FIELD_LANGUAGE, normalizeStoredLanguage(document.getLanguage()));
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
                FIELD_QUESTION_ID,
                FIELD_TITLE,
                FIELD_DIFFICULTY,
                FIELD_LANGUAGE,
                FIELD_IS_DELETE,
                embeddingField
        );
    }

    private List<String> searchOutputFields() {
        return List.of(
                FIELD_ID,
                FIELD_QUESTION_ID,
                FIELD_TITLE,
                FIELD_DIFFICULTY,
                FIELD_LANGUAGE,
                FIELD_IS_DELETE
        );
    }

    private String questionIdFilter(Long questionId) {
        return FIELD_QUESTION_ID + " == " + questionId;
    }

    private List<SearchResp.SearchResult> unwrapRawHits(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        List<SearchResp.SearchResult> firstQueryResults = response.getSearchResults().get(0);
        return firstQueryResults == null ? List.of() : firstQueryResults;
    }

    private VectorSearchHit toSearchHit(SearchResp.SearchResult result) {
        if (result == null) {
            return null;
        }
        Map<String, Object> entity = result.getEntity();
        if (entity == null) {
            return null;
        }
        if (isDeleted(entity.get(FIELD_IS_DELETE))) {
            return null;
        }
        Long questionId = toLong(entity.get(FIELD_QUESTION_ID));
        if (questionId == null) {
            questionId = toLong(result.getId());
        }
        return VectorSearchHit.builder()
                .documentId(toText(entity.get(FIELD_ID)))
                .questionId(questionId)
                .title(toText(entity.get(FIELD_TITLE)))
                .difficulty(toText(entity.get(FIELD_DIFFICULTY)))
                .language(normalizeProjectedLanguage(toText(entity.get(FIELD_LANGUAGE))))
                .similarity(toSimilarityScore(result.getScore()))
                .finalScore(toSimilarityScore(result.getScore()))
                .build();
    }

    private VectorSearchHit withFinalScore(VectorSearchHit source, double finalScore) {
        return VectorSearchHit.builder()
                .documentId(source.getDocumentId())
                .questionId(source.getQuestionId())
                .title(source.getTitle())
                .difficulty(source.getDifficulty())
                .language(source.getLanguage())
                .similarity(score(source.getSimilarity()))
                .finalScore(finalScore)
                .build();
    }

    private VectorDocument toVectorDocument(Map<String, Object> entity, Long fallbackQuestionId) {
        if (entity == null || entity.isEmpty()) {
            return null;
        }
        Long questionId = toLong(entity.get(FIELD_QUESTION_ID));
        if (questionId == null) {
            questionId = fallbackQuestionId;
        }
        if (questionId == null) {
            return null;
        }
        return VectorDocument.builder()
                .id(toText(entity.get(FIELD_ID)))
                .questionId(questionId)
                .title(toText(entity.get(FIELD_TITLE)))
                .difficulty(toText(entity.get(FIELD_DIFFICULTY)))
                .language(normalizeProjectedLanguage(toText(entity.get(FIELD_LANGUAGE))))
                .embedding(toFloatList(entity.get(embeddingField)))
                .deleted(isDeleted(entity.get(FIELD_IS_DELETE)))
                .build();
    }

    private double computeFinalScore(VectorSearchHit hit, VectorSearchRequest request) {
        double preferenceWeight = score(request.getPreferenceWeight());
        double boost = 0D;
        if (matchesPreferred(request.getPreferredDifficulty(), hit.getDifficulty())) {
            boost += preferenceWeight;
        }
        if (matchesPreferred(request.getPreferredLanguage(), hit.getLanguage())) {
            boost += preferenceWeight;
        }
        return score(hit.getSimilarity()) + boost;
    }

    private boolean matchesFilter(String expected, String actual) {
        if (isBlank(expected)) {
            return true;
        }
        return Objects.equals(expected.trim(), actual == null ? null : actual.trim());
    }

    private boolean matchesLanguageFilter(String expected, String actual) {
        if (isBlank(expected)) {
            return true;
        }
        if (isBlank(actual)) {
            return true;
        }
        return Objects.equals(expected.trim(), actual.trim());
    }

    private boolean matchesPreferred(String preferred, String actual) {
        if (isBlank(preferred)) {
            return false;
        }
        return Objects.equals(preferred.trim(), actual == null ? null : actual.trim());
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

    private String normalizeStoredLanguage(String language) {
        return isBlank(language) ? LANGUAGE_SHARED : language.trim();
    }

    private String normalizeProjectedLanguage(String language) {
        if (isBlank(language)) {
            return null;
        }
        String normalized = language.trim();
        return LANGUAGE_SHARED.equalsIgnoreCase(normalized) ? null : normalized;
    }

    private String requireText(String value, String errorMessage) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }
}
