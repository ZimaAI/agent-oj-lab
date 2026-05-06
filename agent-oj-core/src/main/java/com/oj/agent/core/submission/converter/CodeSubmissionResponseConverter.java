package com.oj.agent.core.submission.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.submission.model.response.CodeExecutionResponse;
import com.oj.agent.core.submission.model.response.CodeSubmissionDetailResponse;
import com.oj.agent.core.submission.model.response.CodeSubmissionListItemResponse;
import com.oj.agent.core.submission.model.result.CodeExecutionResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;

import java.util.List;

public final class CodeSubmissionResponseConverter {

    private CodeSubmissionResponseConverter() {
    }

    public static CodeExecutionResponse toExecutionResponse(CodeExecutionResult result) {
        if (result == null) {
            return null;
        }
        CodeExecutionResponse response = new CodeExecutionResponse();
        response.setSuccess(result.getSuccess());
        List<CodeExecutionResponse.TestResultItem> records = result.getResults() == null
                ? List.of()
                : result.getResults().stream()
                .map(item -> new CodeExecutionResponse.TestResultItem(
                        item.getPassed(),
                        item.getOutput(),
                        item.getError()))
                .toList();
        response.setResults(records);
        response.setErrorMessage(result.getErrorMessage());
        return response;
    }

    public static CodeSubmissionListItemResponse toListItemResponse(CodeSubmissionResult result) {
        if (result == null) {
            return null;
        }
        CodeSubmissionListItemResponse response = new CodeSubmissionListItemResponse();
        response.setId(result.getId());
        response.setAlgorithmQuestionId(result.getAlgorithmQuestionId());
        response.setQuestionTitle(result.getQuestionTitle());
        response.setPassCount(result.getPassCount());
        response.setTotalCount(result.getTotalCount());
        response.setLanguage(result.getLanguage());
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static CodeSubmissionDetailResponse toDetailResponse(CodeSubmissionResult result) {
        if (result == null) {
            return null;
        }
        CodeSubmissionDetailResponse response = new CodeSubmissionDetailResponse();
        response.setId(result.getId());
        response.setAlgorithmQuestionId(result.getAlgorithmQuestionId());
        response.setQuestionTitle(result.getQuestionTitle());
        response.setCode(result.getCode());
        response.setLanguage(result.getLanguage());
        response.setTestResults(result.getTestResults());
        response.setPassCount(result.getPassCount());
        response.setTotalCount(result.getTotalCount());
        response.setCodeEvaluation(result.getCodeEvaluation());
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static Page<CodeSubmissionListItemResponse> toListItemResponsePage(Page<CodeSubmissionResult> resultPage) {
        Page<CodeSubmissionListItemResponse> responsePage = new Page<>();
        if (resultPage == null) {
            return responsePage;
        }
        responsePage.setCurrent(resultPage.getCurrent());
        responsePage.setSize(resultPage.getSize());
        responsePage.setTotal(resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(CodeSubmissionResponseConverter::toListItemResponse)
                .toList());
        return responsePage;
    }
}
