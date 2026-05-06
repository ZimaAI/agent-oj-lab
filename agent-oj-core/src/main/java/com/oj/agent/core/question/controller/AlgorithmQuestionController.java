package com.oj.agent.core.question.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.common.api.Result;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.model.request.AlgorithmQuestionPageRequest;
import com.oj.agent.core.question.model.request.TagPageRequest;
import com.oj.agent.core.question.model.response.AlgorithmQuestionResponse;
import com.oj.agent.core.question.model.response.TagResponse;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.question.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AlgorithmQuestionController {

    // 算法题服务。
    private final AlgorithmQuestionService algorithmQuestionService;

    // 标签服务。
    private final TagService tagService;

    public AlgorithmQuestionController(AlgorithmQuestionService algorithmQuestionService,
                                       TagService tagService) {
        this.algorithmQuestionService = algorithmQuestionService;
        this.tagService = tagService;
    }

    // 查询全部标签列表。
    @GetMapping("/tag/list")
    public Result<List<TagResponse>> listTags() {
        return Result.success(AlgorithmQuestionConverter.toTagResponseList(tagService.listTags()));
    }

    // 分页查询标签列表。
    @GetMapping("/tag/page")
    public Result<Page<TagResponse>> pageTags(TagPageRequest request,
                                              @RequestParam(value = "pageSize", required = false) Long pageSize,
                                              @RequestParam(value = "size", required = false) Long size) {
        TagPageRequest safeRequest = request == null ? new TagPageRequest() : request;
        // 兼容前端使用 size 作为分页大小参数的场景。
        if (pageSize == null && size != null) {
            safeRequest.setPageSize(size);
        }
        return Result.success(AlgorithmQuestionConverter.toTagResponsePage(
                tagService.pageTags(AlgorithmQuestionConverter.toTagPageQuery(safeRequest))
        ));
    }

    // 分页查询算法题列表。
    @PostMapping("/algorithm-question/page")
    public Result<Page<AlgorithmQuestionResponse>> pageQuestions(@RequestBody(required = false) AlgorithmQuestionPageRequest request) {
        return Result.success(AlgorithmQuestionConverter.toAlgorithmQuestionResponsePage(
                algorithmQuestionService.pageQuestions(AlgorithmQuestionConverter.toPageQuery(request))
        ));
    }

    // 查询算法题详情。
    @GetMapping("/algorithm-question/{id}")
    public Result<AlgorithmQuestionResponse> getQuestionById(@PathVariable Long id) {
        return Result.success(AlgorithmQuestionConverter.toAlgorithmQuestionResponse(
                algorithmQuestionService.getQuestion(AlgorithmQuestionConverter.toGetQuery(id))
        ));
    }
}
