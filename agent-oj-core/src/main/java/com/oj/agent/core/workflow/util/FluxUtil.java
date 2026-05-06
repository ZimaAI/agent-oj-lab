package com.oj.agent.core.workflow.util;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.result.TraceStatusResult;
import com.oj.agent.core.trace.model.result.TraceTokenUsageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public final class FluxUtil {

    private FluxUtil() {
    }

    public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
                                             Function<Flux<T>, Mono<R>> aggregator, Flux<T> preFlux,
                                             Flux<T> middleFlux, Flux<T> endFlux) {
        Flux<T> cachedOrigin = originFlux.cache();
        Mono<R> aggregatedResult = aggregator.apply(cachedOrigin).cache();
        Flux<T> secondFlux = aggregatedResult.flatMapMany(nextFluxFunc);
        return preFlux.concatWith(cachedOrigin).concatWith(middleFlux).concatWith(secondFlux).concatWith(endFlux);
    }

    public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
                                             Function<Flux<T>, Mono<R>> aggregator) {
        return cascadeFlux(originFlux, nextFluxFunc, aggregator, Flux.empty(), Flux.empty(), Flux.empty());
    }

    public static Flux<GraphResponse<StreamingOutput>> createStreamingGeneratorWithMessages(
            Class<? extends NodeAction> nodeClass, OverAllState state, String startMessage, String completionMessage,
            Flux<ChatResponse> sourceFlux, Function<String, Map<String, Object>> resultMapper) {
        String nodeName = nodeClass.getSimpleName();
        StringBuilder collectedResult = new StringBuilder();

        Flux<ChatResponse> startFlux = startMessage == null ? Flux.empty()
                : Flux.just(ChatResponseUtil.createResponse(startMessage));
        Flux<ChatResponse> wrapperFlux = startFlux.concatWith(sourceFlux.doOnNext(chatResponse -> {
            String text = ChatResponseUtil.getText(chatResponse);
            collectedResult.append(text);
        }));
        if (completionMessage != null) {
            wrapperFlux = wrapperFlux.concatWith(Flux.just(ChatResponseUtil.createResponse(completionMessage)));
        }
        return toStreamingResponseFlux(nodeName, state, wrapperFlux,
                () -> resultMapper.apply(collectedResult.toString()));
    }

    public static Flux<GraphResponse<StreamingOutput>> createStreamingGeneratorWithMessages(
            Class<? extends NodeAction> nodeClass, OverAllState state, Flux<ChatResponse> sourceFlux,
            Function<String, Map<String, Object>> resultMapper) {
        return createStreamingGeneratorWithMessages(nodeClass, state, null, null, sourceFlux, resultMapper);
    }

    public static Flux<GraphResponse<StreamingOutput>> createStreamingGenerator(Class<? extends NodeAction> nodeClass,
                                                                                OverAllState state,
                                                                                Flux<ChatResponse> sourceFlux,
                                                                                Flux<ChatResponse> preFlux,
                                                                                Flux<ChatResponse> sufFlux,
                                                                                Function<String, Map<String, Object>> sourceMapper) {
        return createStreamingGenerator(nodeClass, state, sourceFlux, preFlux, sufFlux, sourceMapper,
                null, null, null);
    }

    public static Flux<GraphResponse<StreamingOutput>> createStreamingGenerator(Class<? extends NodeAction> nodeClass,
                                                                                OverAllState state,
                                                                                Flux<ChatResponse> sourceFlux,
                                                                                Flux<ChatResponse> preFlux,
                                                                                Flux<ChatResponse> sufFlux,
                                                                                Function<String, Map<String, Object>> sourceMapper,
                                                                                WorkflowTraceRecorder workflowTraceRecorder,
                                                                                Long traceItemId,
                                                                                Function<String, String> outputPayloadMapper) {
        String nodeName = nodeClass.getSimpleName();
        StringBuilder collectedResult = new StringBuilder();
        AtomicInteger promptTokens = new AtomicInteger();
        AtomicInteger completionTokens = new AtomicInteger();
        AtomicInteger totalTokens = new AtomicInteger();
        AtomicBoolean hasPromptTokens = new AtomicBoolean(false);
        AtomicBoolean hasCompletionTokens = new AtomicBoolean(false);
        AtomicBoolean hasTotalTokens = new AtomicBoolean(false);

        Flux<ChatResponse> collectedSourceFlux = sourceFlux.doOnNext(response -> {
            collectedResult.append(ChatResponseUtil.getText(response));
            TraceTokenUsageResult usage = ChatResponseUtil.extractTokenUsage(response);
            addIfPresent(promptTokens, hasPromptTokens, usage.promptTokens());
            addIfPresent(completionTokens, hasCompletionTokens, usage.completionTokens());
            addIfPresent(totalTokens, hasTotalTokens, usage.totalTokens());
        });

        Supplier<Map<String, Object>> resultSupplier = () -> {
            String output = collectedResult.toString();
            tryBackfillSuccessTraceItem(workflowTraceRecorder, traceItemId, outputPayloadMapper, output,
                    buildTokenUsage(promptTokens, hasPromptTokens, completionTokens, hasCompletionTokens,
                            totalTokens, hasTotalTokens));
            return sourceMapper.apply(output);
        };
        return toStreamingResponseFlux(nodeName, state, Flux.concat(preFlux, collectedSourceFlux, sufFlux), resultSupplier,
                error -> backfillFailureTraceItem(workflowTraceRecorder, traceItemId, error,
                        buildTokenUsage(promptTokens, hasPromptTokens, completionTokens, hasCompletionTokens,
                                totalTokens, hasTotalTokens)));
    }

    private static Flux<GraphResponse<StreamingOutput>> toStreamingResponseFlux(String nodeName, OverAllState state,
                                                                                Flux<ChatResponse> sourceFlux,
                                                                                Supplier<Map<String, Object>> resultSupplier) {
        return toStreamingResponseFlux(nodeName, state, sourceFlux, resultSupplier, error -> Mono.empty());
    }

    private static Flux<GraphResponse<StreamingOutput>> toStreamingResponseFlux(String nodeName, OverAllState state,
                                                                                Flux<ChatResponse> sourceFlux,
                                                                                Supplier<Map<String, Object>> resultSupplier,
                                                                                Function<Throwable, Mono<Void>> errorHandler) {
        Flux<GraphResponse<StreamingOutput>> streamingFlux = sourceFlux
                .filter(response -> response != null && response.getResult() != null
                        && response.getResult().getOutput() != null)
                .map(response -> GraphResponse.of(new StreamingOutput<>(response.getResult().getOutput(), response,
                        nodeName, "", state, OutputType.from(true, nodeName))));

        return streamingFlux.concatWith(Mono.fromSupplier(() -> GraphResponse.done(resultSupplier.get())))
                .onErrorResume(error -> errorHandler.apply(error)
                        .onErrorResume(traceError -> Mono.empty())
                        .thenMany(Flux.just(GraphResponse.error(error))));
    }

    // 回填成功态 trace_item，并保持失败为 best-effort。
    private static void tryBackfillSuccessTraceItem(WorkflowTraceRecorder workflowTraceRecorder, Long traceItemId,
                                                    Function<String, String> outputPayloadMapper, String output,
                                                    TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || traceItemId == null) {
            return;
        }
        try {
            String outputPayload = outputPayloadMapper == null ? output : outputPayloadMapper.apply(output);
            workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                    .traceItemId(traceItemId)
                    .outputPayload(outputPayload)
                    .status(TraceStatusResult.SUCCESS)
                    .tokenUsage(tokenUsage)
                    .endTimestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.warn("回填成功态 trace_item 失败，继续保持流式输出。traceItemId={}", traceItemId, e);
        }
    }

    // 回填失败态 trace_item，并保留已累计的 source token。
    private static Mono<Void> backfillFailureTraceItem(WorkflowTraceRecorder workflowTraceRecorder, Long traceItemId,
                                                       Throwable error, TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || traceItemId == null) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .errorMessage(error == null ? null : error.getMessage())
                .status(TraceStatusResult.FAILED)
                .tokenUsage(tokenUsage)
                .endTimestamp(System.currentTimeMillis())
                .build()));
    }

    // 仅在 token 存在时参与累计，避免把缺失值误记为 0。
    private static void addIfPresent(AtomicInteger accumulator, AtomicBoolean presentFlag, Integer value) {
        if (value == null) {
            return;
        }
        accumulator.addAndGet(value);
        presentFlag.set(true);
    }

    // 构造累计后的 token usage，并保留空值语义。
    private static TraceTokenUsageResult buildTokenUsage(AtomicInteger promptTokens, AtomicBoolean hasPromptTokens,
                                                   AtomicInteger completionTokens, AtomicBoolean hasCompletionTokens,
                                                   AtomicInteger totalTokens, AtomicBoolean hasTotalTokens) {
        Integer promptValue = hasPromptTokens.get() ? promptTokens.get() : null;
        Integer completionValue = hasCompletionTokens.get() ? completionTokens.get() : null;
        Integer totalValue = hasTotalTokens.get() ? totalTokens.get() : null;
        return new TraceTokenUsageResult(promptValue, completionValue, totalValue);
    }
}
