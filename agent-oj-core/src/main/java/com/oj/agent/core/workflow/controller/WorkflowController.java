package com.oj.agent.core.workflow.controller;

import com.oj.agent.core.workflow.converter.WorkflowConverter;
import com.oj.agent.core.workflow.model.request.CodeEvaluationStreamRequest;
import com.oj.agent.core.workflow.model.request.UserMessageRequest;
import com.oj.agent.core.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody UserMessageRequest userMessageRequest) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        workflowService.stream(emitter, WorkflowConverter.toStartCommand(userMessageRequest));
        return emitter;
    }

    @PostMapping(value = "/code-evaluation/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter codeEvaluationStream(@RequestBody CodeEvaluationStreamRequest codeEvaluationStreamRequest) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        workflowService.stream(emitter, WorkflowConverter.toCodeEvaluationStartCommand(codeEvaluationStreamRequest));
        return emitter;
    }
}
