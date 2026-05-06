package com.oj.agent.core.rag.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.common.api.Result;
import com.oj.agent.core.rag.converter.KnowledgeDocumentConverter;
import com.oj.agent.core.rag.model.request.KnowledgeDocumentBatchParseByUrlRequest;
import com.oj.agent.core.rag.model.request.KnowledgeDocumentParseByUrlRequest;
import com.oj.agent.core.rag.model.response.KnowledgeDocumentParseResponse;
import com.oj.agent.core.rag.model.response.KnowledgeDocumentResponse;
import com.oj.agent.core.rag.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @PostMapping("/parse/url")
    public Result<KnowledgeDocumentParseResponse> parseByUrl(@Valid @RequestBody KnowledgeDocumentParseByUrlRequest request) {
        return Result.success(KnowledgeDocumentConverter.toParseResponse(
                knowledgeDocumentService.parseByUrl(KnowledgeDocumentConverter.toParseCommand(request))
        ));
    }

    @PostMapping("/parse/url/batch")
    public Result<List<KnowledgeDocumentParseResponse>> parseByUrlBatch(
            @Valid @RequestBody KnowledgeDocumentBatchParseByUrlRequest request) {
        return Result.success(KnowledgeDocumentConverter.toParseResponseList(
                knowledgeDocumentService.parseByUrlBatch(KnowledgeDocumentConverter.toBatchParseCommand(request))
        ));
    }

    @PostMapping(value = "/parse/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentParseResponse> parseByUpload(@RequestPart("file") MultipartFile file) {
        return Result.success(KnowledgeDocumentConverter.toParseResponse(knowledgeDocumentService.parseByUpload(file)));
    }

    @PostMapping(value = "/parse/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<KnowledgeDocumentParseResponse>> parseByUploadBatch(@RequestPart("files") List<MultipartFile> files) {
        return Result.success(KnowledgeDocumentConverter.toParseResponseList(knowledgeDocumentService.parseByUploadBatch(files)));
    }

    @GetMapping("/page")
    public Result<Page<KnowledgeDocumentResponse>> page(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                                        @RequestParam(value = "pageSize", defaultValue = "20") long pageSize) {
        return Result.success(KnowledgeDocumentConverter.toDocumentResponsePage(
                knowledgeDocumentService.pageCurrentUserDocuments(pageNum, pageSize)
        ));
    }

    @GetMapping("/{docId}")
    public Result<KnowledgeDocumentResponse> getById(@PathVariable Long docId) {
        return Result.success(KnowledgeDocumentConverter.toDocumentResponse(
                knowledgeDocumentService.getCurrentUserDocument(docId)
        ));
    }
}
