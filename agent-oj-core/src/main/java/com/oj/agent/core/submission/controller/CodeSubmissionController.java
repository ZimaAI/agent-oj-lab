package com.oj.agent.core.submission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.common.api.Result;
import com.oj.agent.core.submission.converter.CodeSubmissionConverter;
import com.oj.agent.core.submission.converter.CodeSubmissionResponseConverter;
import com.oj.agent.core.submission.model.request.CodeRunRequest;
import com.oj.agent.core.submission.model.request.CodeSubmissionPageRequest;
import com.oj.agent.core.submission.model.response.CodeExecutionResponse;
import com.oj.agent.core.submission.model.response.CodeSubmissionDetailResponse;
import com.oj.agent.core.submission.model.response.CodeSubmissionListItemResponse;
import com.oj.agent.core.submission.service.CodeSubmissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/code")
public class CodeSubmissionController {

    // 代码提交服务。
    private final CodeSubmissionService codeSubmissionService;

    public CodeSubmissionController(CodeSubmissionService codeSubmissionService) {
        this.codeSubmissionService = codeSubmissionService;
    }

    // 执行代码。
    @PostMapping("/execute")
    public Result<CodeExecutionResponse> executeCode(@RequestBody CodeRunRequest request) {
        return Result.success(CodeSubmissionResponseConverter.toExecutionResponse(
                codeSubmissionService.executeCode(CodeSubmissionConverter.toRunCommand(request))
        ));
    }

    // 分页查询提交记录。
    @PostMapping("/submissions")
    public Result<Page<CodeSubmissionListItemResponse>> pageSubmissions(
            @RequestBody(required = false) CodeSubmissionPageRequest request) {
        return Result.success(CodeSubmissionResponseConverter.toListItemResponsePage(
                codeSubmissionService.pageSubmissions(CodeSubmissionConverter.toPageQuery(request))
        ));
    }

    // 查询提交详情。
    @GetMapping("/submissions/{id}")
    public Result<CodeSubmissionDetailResponse> getSubmissionDetail(@PathVariable Long id) {
        return Result.success(CodeSubmissionResponseConverter.toDetailResponse(
                codeSubmissionService.getSubmission(CodeSubmissionConverter.toGetQuery(id))
        ));
    }
}
