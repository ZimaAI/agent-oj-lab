package com.oj.agent.core.workflow.service;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.workflow.converter.WorkflowConverter;
import com.oj.agent.core.workflow.model.command.WorkflowStartCommand;
import com.oj.agent.core.workflow.model.result.WorkflowBootstrapResult;
import com.oj.agent.core.workflow.model.result.WorkflowStreamEventResult;
import com.oj.agent.core.workflow.persistence.WorkflowPersistenceChain;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;
import com.oj.agent.core.workflow.service.context.StreamContext;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.security.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WorkflowService {

    private final CompiledGraph ojCompiledGraph;
    private final WorkflowPersistenceChain workflowPersistenceChain;
    private final WorkflowBootstrapService workflowBootstrapService;
    private final WorkflowTraceRecorder workflowTraceRecorder;
    private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

    public WorkflowService(StateGraph ojGraph,
                           @Qualifier("mysqlDataSource") DataSource dataSource,
                           WorkflowPersistenceChain workflowPersistenceChain,
                           WorkflowBootstrapService workflowBootstrapService,
                           WorkflowTraceRecorder workflowTraceRecorder) throws GraphStateException {
        MysqlSaver mysqlSaver = MysqlSaver.builder()
                .dataSource(dataSource)
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .build();

        SaverConfig saverConfig = SaverConfig.builder()
                .register(mysqlSaver)
                .build();

        this.ojCompiledGraph = ojGraph.compile(
                CompileConfig.builder()
                        .saverConfig(saverConfig)
                        .build()
        );
        this.workflowPersistenceChain = workflowPersistenceChain;
        this.workflowBootstrapService = workflowBootstrapService;
        this.workflowTraceRecorder = workflowTraceRecorder;
    }

    // 统一处理普通对话与代码评审入口。
    public void stream(SseEmitter emitter, WorkflowStartCommand startCommand) {
        StreamStartPayload payload;
        try {
            payload = resolveStreamStartPayload(startCommand);
        } catch (IllegalArgumentException exception) {
            handleInvalidStartCommand(emitter, startCommand, exception);
            return;
        }

        WorkflowBootstrapResult bootstrapResult = bootstrapWithFallback(emitter, payload);
        if (bootstrapResult == null) {
            return;
        }

        // 在图执行前记录链路级 trace 启动信息。
        startTraceRecord(payload.traceId(), payload.conversationId(), payload.requestMessage(), bootstrapResult);

        StreamContext context = prepareStreamContext(payload.conversationId(), emitter);
        if (context == null) {
            return;
        }

        Map<String, Object> inputMap = buildInputMap(
                payload.conversationId(),
                payload.traceId(),
                payload.requestMessage(),
                bootstrapResult
        );
        Flux<NodeOutput> nodeOutputFlux = startNodeOutputFlux(payload.traceId(), payload.conversationId(), inputMap);
        if (nodeOutputFlux == null) {
            return;
        }

        subscribeNodeOutputAsync(context, payload.conversationId(), payload.traceId(), nodeOutputFlux);
    }

    // 解析并校验流式启动参数。
    private StreamStartPayload resolveStreamStartPayload(WorkflowStartCommand startCommand) {
        if (startCommand == null) {
            throw new IllegalArgumentException("WorkflowStartCommand cannot be null");
        }

        boolean codeEvaluation = startCommand.isCodeEvaluation();
        String requestMessage = resolveRequestMessage(startCommand);
        if (codeEvaluation && !StringUtils.hasText(startCommand.getCode())) {
            throw new IllegalArgumentException("WorkflowStartCommand.code cannot be empty for code evaluation request");
        }
        if (!StringUtils.hasText(requestMessage)) {
            throw new IllegalArgumentException("WorkflowStartCommand.message cannot be empty for conversation request");
        }

        String conversationId = startCommand.getConversationId();
        boolean isNewConversation = !codeEvaluation && !StringUtils.hasText(conversationId);
        if (isNewConversation) {
            conversationId = UUID.randomUUID().toString();
        }
        String traceId = UUID.randomUUID().toString();
        return new StreamStartPayload(
                codeEvaluation,
                startCommand,
                isNewConversation,
                conversationId,
                requestMessage,
                traceId
        );
    }

    // 启动前执行 bootstrap，失败时收敛 trace 并回写错误。
    private WorkflowBootstrapResult bootstrapWithFallback(SseEmitter emitter, StreamStartPayload payload) {
        try {
            // 图执行前先完成入口所需的上下文初始化。
            return payload.codeEvaluation()
                    ? workflowBootstrapService.bootstrapCodeEvaluation(
                    payload.startCommand(),
                    payload.conversationId(),
                    payload.traceId()
            )
                    : workflowBootstrapService.bootstrap(
                    payload.startCommand(),
                    payload.isNewConversation(),
                    payload.conversationId(),
                    payload.traceId()
            );
        } catch (Exception e) {
            // 兜底补建失败链路 trace，避免 bootstrap 失败时缺少 trace 主记录。
            startTraceRecord(payload.traceId(), payload.conversationId(), payload.requestMessage(), null);
            handleStartupError(payload.traceId(), payload.conversationId(), emitter, e);
            return null;
        }
    }

    private void handleInvalidStartCommand(SseEmitter emitter,
                                           WorkflowStartCommand startCommand,
                                           IllegalArgumentException exception) {
        String conversationId = startCommand == null ? null : startCommand.getConversationId();
        log.warn("Reject invalid workflow start command, conversationId={}", conversationId, exception);
        sendErrorEvent(emitter, conversationId, exception);
    }

    private String resolveRequestMessage(WorkflowStartCommand startCommand) {
        if (startCommand == null) {
            return null;
        }
        if (!startCommand.isCodeEvaluation()) {
            return startCommand.getMessage();
        }
        if (StringUtils.hasText(startCommand.getMessage())) {
            return startCommand.getMessage();
        }
        return startCommand.getCode();
    }

    // 准备会话流式上下文并校验可用性。
    private StreamContext prepareStreamContext(String conversationId, SseEmitter emitter) {
        StreamContext context = streamContextMap.computeIfAbsent(conversationId, k -> new StreamContext());
        context.setEmitter(emitter);

        if (context.getEmitter() == null) {
            throw new IllegalStateException("StreamContext not found for threadId: " + conversationId);
        }
        if (context.isCleaned()) {
            log.warn("StreamContext already cleaned for threadId: {}, skipping stream start", context);
            return null;
        }
        return context;
    }

    // 启动 graph 流式输出并处理同步启动异常。
    private Flux<NodeOutput> startNodeOutputFlux(String traceId,
                                                 String conversationId,
                                                 Map<String, Object> inputMap) {
        try {
            return ojCompiledGraph.stream(inputMap, RunnableConfig.builder().threadId(conversationId).build());
        } catch (Exception e) {
            // 捕获图启动阶段同步异常，确保 trace 能按失败态收敛。
            handleStreamError(traceId, conversationId, e);
            return null;
        }
    }

    // 异步订阅节点输出并维护会话级 Disposable 生命周期。
    private void subscribeNodeOutputAsync(StreamContext context,
                                          String conversationId,
                                          String traceId,
                                          Flux<NodeOutput> nodeOutputFlux) {
        CompletableFuture.runAsync(() -> {
            try {
                if (context.isCleaned()) {
                    log.debug("StreamContext cleaned before subscription for threadId: {}", conversationId);
                    return;
                }
                Disposable disposable = nodeOutputFlux.subscribe(
                        output -> handleNodeOutput(conversationId, output),
                        error -> handleStreamError(traceId, conversationId, error),
                        () -> handleStreamComplete(conversationId)
                );
                synchronized (context) {
                    if (context.isCleaned()) {
                        if (!disposable.isDisposed()) {
                            disposable.dispose();
                        }
                    } else {
                        // 只有在未清理的情况下才设置 Disposable
                        context.setDisposable(disposable);
                    }
                }
            } catch (Exception e) {
                // 捕获异步订阅准备阶段异常并沿用现有流式错误处理。
                handleStreamError(traceId, conversationId, e);
            }
        });
    }

    // 组装图启动输入，统一重置上轮节点状态。
    private Map<String, Object> buildInputMap(String conversationId,
                                              String traceId,
                                              String requestMessage,
                                              WorkflowBootstrapResult bootstrapResult) {
        return WorkflowInputMapBuilder.build(
                conversationId,
                traceId,
                requestMessage,
                UserContext.getUserId(),
                bootstrapResult
        );
    }

    // 启动链路级 trace 记录，写库异常不影响主流程。
    private void startTraceRecord(String traceId,
                                  String conversationId,
                                  String requestMessage,
                                  WorkflowBootstrapResult bootstrapResult) {
        if (workflowTraceRecorder == null) {
            return;
        }
        workflowTraceRecorder.startTrace(
                traceId,
                conversationId,
                UserContext.getUserId(),
                requestMessage,
                bootstrapResult == null ? null : bootstrapResult.getCurrentAlgorithmQuestionSnapshot());
    }

    private void handleNodeOutput(String conversationId, NodeOutput nodeOutput) {
        if (nodeOutput instanceof StreamingOutput streamingOutput) {
            handleStreamNodeOutput(conversationId, streamingOutput);
        } else {
            if (nodeOutput.node().equals(StateGraph.END)){
                // 对非流式节点结果做持久化处理。
                persistWorkflowResults(nodeOutput.state());
                Object traceId = nodeOutput.state().value(Constant.TRACE_ID).orElse(null);
                if (traceId instanceof String traceIdStr && workflowTraceRecorder != null) {
                    workflowTraceRecorder.completeTrace(traceIdStr);
                }
                log.info("NodeOutput State: {}", nodeOutput.state());
            }
        }
    }

    // 统一创建持久化上下文并委派责任链执行。
    public void persistWorkflowResults(OverAllState state) {
        if (state == null) {
            return;
        }

        WorkflowPersistenceContext context = new WorkflowPersistenceContext();
        workflowPersistenceChain.persist(state, context);
    }

    private void handleStreamNodeOutput(String conversationId, StreamingOutput nodeOutput) {
        StreamContext context = streamContextMap.get(conversationId);
        if (context != null && !context.isCleaned()) {
            SseEmitter emitter = context.getEmitter();
            if (emitter != null) {
                String node = nodeOutput.node();
                Object rawMessage = nodeOutput.message();
                if (!(rawMessage instanceof AssistantMessage message)) {
                    log.debug("Skip streaming output without assistant message, node: {}", node);
                    return;
                }
                String chunk = message.getText();
                if (!StringUtils.hasText(chunk)) {
                    return;
                }

                TextType originType = context.getTextType();
                TextType textType;
                boolean isTypeSign = false;
                if (originType == null) {
                    textType = TextType.getTypeByStratSign(chunk);
                    if (textType != TextType.TEXT) {
                        isTypeSign = true;
                    }
                    context.setTextType(textType);
                } else {
                    textType = TextType.getType(originType, chunk);
                    if (textType != originType) {
                        isTypeSign = true;
                    }
                    context.setTextType(textType);
                }
                if (isTypeSign) return;

                WorkflowStreamEventResult response = WorkflowStreamEventResult.builder()
                        .conversationId(conversationId)
                        .nodeName(node)
                        .text(chunk)
                        .textType(textType)
                        .build();

                try {
                    emitter.send(SseEmitter.event().data(WorkflowConverter.toGraphNodeResponse(response)));
                } catch (Exception e) {
                    log.error("Failed to send error event", e);
                }
            }
        }
    }

    // 处理 bootstrap 等启动阶段异常，直接向当前 emitter 回写错误并收敛 trace。
    private void handleStartupError(String traceId, String conversationId, SseEmitter emitter, Throwable error) {
        log.error("Error in stream startup for threadId: {}: ", conversationId, error);
        if (workflowTraceRecorder != null) {
            workflowTraceRecorder.failTrace(traceId, error == null ? null : error.getMessage());
        }
        sendErrorEvent(emitter, conversationId, error);
    }

    private void handleStreamError(String traceId, String threadId, Throwable error) {
        log.error("Error in stream processing for threadId: {}: ", threadId, error);
        if (workflowTraceRecorder != null) {
            workflowTraceRecorder.failTrace(traceId, error == null ? null : error.getMessage());
        }
        StreamContext context = streamContextMap.remove(threadId);
        if (context != null && !context.isCleaned()) {
            SseEmitter emitter = context.getEmitter();
            sendErrorEvent(emitter, threadId, error);
            // 清理资源（cleanup 内部已经保证只执行一次）
            context.cleanup();
        }
    }

    // 统一发送流式错误事件，并尽量补全 emitter 生命周期。
    private void sendErrorEvent(SseEmitter emitter, String conversationId, Throwable error) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(Constant.STREAM_EVENT_ERROR)
                    .data(WorkflowConverter.toGraphNodeResponse(
                            WorkflowStreamEventResult.error(
                                    conversationId,
                                    "error",
                                    "Error in stream processing: " + (error == null ? null : error.getMessage())
                            ))));
        } catch (Exception e) {
            log.error("Failed to send error event", e);
        } finally {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("Failed to complete emitter after error", e);
            }
        }
    }

    private void handleStreamComplete(String threadId) {
        log.info("Stream processing completed successfully for threadId: {}", threadId);
        StreamContext context = streamContextMap.remove(threadId);
        if (context != null && !context.isCleaned()) {
            SseEmitter emitter = context.getEmitter();
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(Constant.STREAM_EVENT_COMPLETE)
                            .data(WorkflowConverter.toGraphNodeResponse(
                                    WorkflowStreamEventResult.complete(threadId, "complete"))));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("Failed to send complete event", e);
                }
            }
            context.cleanup();
        }
    }

    // 封装流式启动所需上下文，避免在主流程中重复传参。
    private record StreamStartPayload(boolean codeEvaluation,
                                      WorkflowStartCommand startCommand,
                                      boolean isNewConversation,
                                      String conversationId,
                                      String requestMessage,
                                      String traceId) {
    }
}
