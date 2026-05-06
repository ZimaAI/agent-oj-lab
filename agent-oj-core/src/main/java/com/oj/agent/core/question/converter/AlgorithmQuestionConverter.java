package com.oj.agent.core.question.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.question.model.dto.AlgorithmQuestionDTO;
import com.oj.agent.core.question.model.entity.AlgorithmCode;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.model.entity.Tag;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.query.AlgorithmQuestionPageQuery;
import com.oj.agent.core.question.model.query.TagPageQuery;
import com.oj.agent.core.question.model.response.AlgorithmCodeTemplateResponse;
import com.oj.agent.core.question.model.request.AlgorithmQuestionPageRequest;
import com.oj.agent.core.question.model.request.TagPageRequest;
import com.oj.agent.core.question.model.response.AlgorithmQuestionResponse;
import com.oj.agent.core.question.model.response.TagResponse;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.model.result.TagResult;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AlgorithmQuestionConverter {

    private AlgorithmQuestionConverter() {
    }

    public static AlgorithmQuestionPageQuery toPageQuery(AlgorithmQuestionPageRequest request) {
        AlgorithmQuestionPageQuery query = new AlgorithmQuestionPageQuery();
        if (request == null) {
            return query;
        }
        query.setPageNum(request.getPageNum());
        query.setPageSize(request.getPageSize());
        query.setDifficulty(request.getDifficulty());
        query.setType(request.getType());
        query.setKeyword(request.getKeyword());
        query.setTagIds(request.getTagIds());
        return query;
    }

    public static TagPageQuery toTagPageQuery(TagPageRequest request) {
        TagPageQuery query = new TagPageQuery();
        if (request == null) {
            return query;
        }
        query.setCurrent(request.getCurrent());
        query.setPageSize(request.getPageSize());
        query.setKeyword(request.getKeyword());
        return query;
    }

    public static AlgorithmQuestionGetQuery toGetQuery(Long id) {
        return new AlgorithmQuestionGetQuery(id);
    }

    public static AlgorithmQuestionResponse toAlgorithmQuestionResponse(AlgorithmQuestionResult result) {
        if (result == null) {
            return null;
        }
        AlgorithmQuestionResponse response = new AlgorithmQuestionResponse();
        response.setId(result.getId());
        response.setTitle(result.getTitle());
        response.setDescription(result.getDescription());
        response.setDifficulty(result.getDifficulty());
        response.setType(result.getType());
        response.setSharedFunctionName(result.getSharedFunctionName());
        response.setSharedCodeSkeleton(result.getSharedCodeSkeleton());
        response.setSharedTestCases(result.getSharedTestCases());
        response.setCodeTemplates(toCodeTemplateResponseList(result.getCodeTemplates()));
        response.setConversationId(result.getConversationId());
        response.setTraceId(result.getTraceId());
        response.setAgentName(result.getAgentName());
        response.setCreateTime(result.getCreateTime());
        response.setUpdateTime(result.getUpdateTime());
        response.setTags(toTagResponseList(result.getTags()));
        response.setSimilarity(result.getSimilarity());
        return response;
    }

    public static Page<AlgorithmQuestionResponse> toAlgorithmQuestionResponsePage(Page<AlgorithmQuestionResult> resultPage) {
        Page<AlgorithmQuestionResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(AlgorithmQuestionConverter::toAlgorithmQuestionResponse)
                .toList());
        return responsePage;
    }

    public static TagResponse toTagResponse(TagResult result) {
        if (result == null) {
            return null;
        }
        TagResponse response = new TagResponse();
        response.setId(result.getId());
        response.setTagName(result.getTagName());
        return response;
    }

    public static AlgorithmCodeTemplateResponse toCodeTemplateResponse(AlgorithmCodeTemplateResult result) {
        if (result == null) {
            return null;
        }
        AlgorithmCodeTemplateResponse response = new AlgorithmCodeTemplateResponse();
        response.setLanguage(result.getLanguage());
        response.setFunctionName(result.getFunctionName());
        response.setCodeSkeleton(result.getCodeSkeleton());
        return response;
    }

    public static List<AlgorithmCodeTemplateResponse> toCodeTemplateResponseList(List<AlgorithmCodeTemplateResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(AlgorithmQuestionConverter::toCodeTemplateResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<TagResponse> toTagResponseList(List<TagResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(AlgorithmQuestionConverter::toTagResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    public static Page<TagResponse> toTagResponsePage(Page<TagResult> resultPage) {
        Page<TagResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(toTagResponseList(resultPage.getRecords()));
        return responsePage;
    }

    public static TagResult toTagResult(Tag tag) {
        if (tag == null) {
            return null;
        }
        TagResult result = new TagResult();
        result.setId(tag.getId());
        result.setTagName(tag.getTagName());
        return result;
    }

    public static AlgorithmCodeTemplateResult toCodeTemplateResult(AlgorithmCode algorithmCode) {
        if (algorithmCode == null) {
            return null;
        }
        return new AlgorithmCodeTemplateResult(
                algorithmCode.getLanguage(),
                algorithmCode.getFunctionName(),
                algorithmCode.getCodeSkeleton()
        );
    }

    public static AlgorithmQuestionResult toPageQuestionResult(AlgorithmQuestion question, List<TagResult> tags) {
        if (question == null) {
            return null;
        }
        AlgorithmQuestionResult result = buildBaseQuestionResult(question, tags);
        result.setSharedFunctionName(question.getSharedFunctionName());
        result.setSharedCodeSkeleton(question.getSharedCodeSkeleton());
        return result;
    }

    public static AlgorithmQuestionResult toDetailQuestionResult(AlgorithmQuestion question,
                                                                 List<TagResult> tags,
                                                                 List<AlgorithmCodeTemplateResult> codeTemplates) {
        AlgorithmQuestionResult result = toPageQuestionResult(question, tags);
        if (result == null) {
            return null;
        }
        result.setSharedTestCases(question.getSharedTestCases());
        result.setCodeTemplates(codeTemplates == null ? Collections.emptyList() : codeTemplates);
        return result;
    }

    public static AlgorithmQuestionDTO toAlgorithmQuestionDTO(AlgorithmQuestionResult result) {
        if (result == null) {
            return null;
        }
        AlgorithmQuestionDTO dto = new AlgorithmQuestionDTO();
        dto.setId(result.getId());
        dto.setTitle(result.getTitle());
        dto.setDescription(result.getDescription());
        dto.setDifficulty(result.getDifficulty());
        dto.setType(result.getType());
        dto.setSharedFunctionName(result.getSharedFunctionName());
        dto.setSharedCodeSkeleton(result.getSharedCodeSkeleton());
        dto.setSharedTestCases(result.getSharedTestCases());
        return dto;
    }

    public static AlgorithmQuestionResult toGeneratedQuestionResult(String title,
                                                                    String description,
                                                                    String difficulty,
                                                                    String sharedFunctionName,
                                                                    String sharedCodeSkeleton,
                                                                    String sharedTestCases,
                                                                    List<String> tagNames) {
        AlgorithmQuestionResult result = new AlgorithmQuestionResult();
        result.setTitle(title);
        result.setDescription(description);
        result.setDifficulty(difficulty);
        result.setSharedFunctionName(sharedFunctionName);
        result.setSharedCodeSkeleton(sharedCodeSkeleton);
        result.setSharedTestCases(sharedTestCases);
        result.setTags(toTagResults(tagNames));
        result.setCodeTemplates(Collections.emptyList());
        return result;
    }

    public static AlgorithmQuestionResult withCodeTemplates(AlgorithmQuestionResult source,
                                                            List<AlgorithmCodeTemplateResult> codeTemplates) {
        if (source == null) {
            return null;
        }
        AlgorithmQuestionResult result = copyQuestionResult(source);
        result.setCodeTemplates(codeTemplates == null ? Collections.emptyList() : codeTemplates);
        return result;
    }

    public static AlgorithmCodeTemplateResult toCodeTemplateResult(String language,
                                                                   String functionName,
                                                                   String codeSkeleton) {
        return new AlgorithmCodeTemplateResult(language, functionName, codeSkeleton);
    }

    public static AlgorithmQuestion toEntity(AlgorithmQuestionResult result) {
        if (result == null) {
            return null;
        }
        AlgorithmQuestion question = new AlgorithmQuestion();
        question.setId(result.getId());
        question.setTitle(result.getTitle());
        question.setDescription(result.getDescription());
        question.setDifficulty(result.getDifficulty());
        question.setType(result.getType());
        question.setSharedFunctionName(result.getSharedFunctionName());
        question.setSharedCodeSkeleton(result.getSharedCodeSkeleton());
        question.setSharedTestCases(result.getSharedTestCases());
        question.setConversationId(result.getConversationId());
        question.setTraceId(result.getTraceId());
        question.setAgentName(result.getAgentName());
        question.setCreateTime(result.getCreateTime());
        question.setUpdateTime(result.getUpdateTime());
        question.setTags(toTagNames(result.getTags()));
        return question;
    }

    public static List<TagResult> toTagResults(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Collections.emptyList();
        }
        return tagNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(tagName -> {
                    TagResult tagResult = new TagResult();
                    tagResult.setTagName(tagName);
                    return tagResult;
                })
                .toList();
    }

    public static List<String> toTagNames(List<TagResult> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(TagResult::getTagName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private static AlgorithmQuestionResult buildBaseQuestionResult(AlgorithmQuestion question, List<TagResult> tags) {
        AlgorithmQuestionResult result = new AlgorithmQuestionResult();
        result.setId(question.getId());
        result.setTitle(question.getTitle());
        result.setDescription(question.getDescription());
        result.setDifficulty(question.getDifficulty());
        result.setType(question.getType());
        result.setConversationId(question.getConversationId());
        result.setTraceId(question.getTraceId());
        result.setAgentName(question.getAgentName());
        result.setCreateTime(question.getCreateTime());
        result.setUpdateTime(question.getUpdateTime());
        result.setTags(tags == null ? Collections.emptyList() : tags);
        return result;
    }

    private static AlgorithmQuestionResult copyQuestionResult(AlgorithmQuestionResult source) {
        AlgorithmQuestionResult result = new AlgorithmQuestionResult();
        result.setId(source.getId());
        result.setTitle(source.getTitle());
        result.setDescription(source.getDescription());
        result.setDifficulty(source.getDifficulty());
        result.setType(source.getType());
        result.setSharedFunctionName(source.getSharedFunctionName());
        result.setSharedCodeSkeleton(source.getSharedCodeSkeleton());
        result.setSharedTestCases(source.getSharedTestCases());
        result.setConversationId(source.getConversationId());
        result.setTraceId(source.getTraceId());
        result.setAgentName(source.getAgentName());
        result.setCreateTime(source.getCreateTime());
        result.setUpdateTime(source.getUpdateTime());
        result.setSimilarity(source.getSimilarity());
        result.setTags(source.getTags() == null ? Collections.emptyList() : source.getTags());
        result.setCodeTemplates(source.getCodeTemplates() == null ? Collections.emptyList() : source.getCodeTemplates());
        return result;
    }
}
