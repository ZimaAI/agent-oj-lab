package com.oj.agent.core.workflow.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.converter.WorkflowConverter;
import com.oj.agent.core.workflow.model.dto.AnswerRewriteOutputDTO;
import com.oj.agent.core.workflow.model.dto.AnswerRAGOutputDTO;
import com.oj.agent.core.workflow.model.dto.AnswerRAGSegmentDTO;
import com.oj.agent.core.workflow.model.result.AnswerHitKGraphResult;
import com.oj.agent.core.workflow.util.NodeBeanUtil;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AnswerHitkGraphService {

    private final CompiledGraph compiledGraph;

    public AnswerHitkGraphService(NodeBeanUtil nodeBeanUtil) throws GraphStateException {
        this.compiledGraph = buildStateGraph(nodeBeanUtil).compile();
    }

    // 构建仅包含答疑改写与答疑检索的最小图。
    private StateGraph buildStateGraph(NodeBeanUtil nodeBeanUtil) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(Constant.ANSWER_REWRITE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.ANSWER_RAG_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.MESSAGES, KeyStrategy.APPEND);
            keyStrategyMap.put(Constant.TRACE_ID, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.CURRENT_ALGORITHM_QUESTION, KeyStrategy.REPLACE);
            return keyStrategyMap;
        };

        StateGraph graph = new StateGraph("answerHitkGraph", keyStrategyFactory);
        graph.addNode(Constant.ANSWER_REWRITE_NODE, nodeBeanUtil.getNodeBeanAsync(com.oj.agent.core.workflow.node.AnswerRewriteNode.class));
        graph.addNode(Constant.ANSWER_RAG_NODE, nodeBeanUtil.getNodeBeanAsync(com.oj.agent.core.workflow.node.AnswerRAGNode.class));
        graph.addEdge(StateGraph.START, Constant.ANSWER_REWRITE_NODE);
        graph.addEdge(Constant.ANSWER_REWRITE_NODE, Constant.ANSWER_RAG_NODE);
        graph.addEdge(Constant.ANSWER_RAG_NODE, StateGraph.END);
        return graph;
    }

    // 执行 Hit@K 图并返回答疑检索命中的知识片段。
    public AnswerHitKGraphResult execute(String hitkQuestion, AlgorithmQuestionResult currentQuestion) {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put(Constant.MESSAGES, List.of(new UserMessage(hitkQuestion)));
        inputMap.put(Constant.TRACE_ID, UUID.randomUUID().toString());
        inputMap.put(Constant.CURRENT_ALGORITHM_QUESTION, currentQuestion == null ? OverAllState.MARK_FOR_REMOVAL : currentQuestion);
        inputMap.put(Constant.ANSWER_REWRITE_NODE_OUTPUT, OverAllState.MARK_FOR_REMOVAL);
        inputMap.put(Constant.ANSWER_RAG_NODE_OUTPUT, OverAllState.MARK_FOR_REMOVAL);

        Optional<OverAllState> finalState = compiledGraph.invoke(
                inputMap,
                RunnableConfig.builder().threadId("answer-hitk-" + UUID.randomUUID()).build()
        );
        AnswerRAGOutputDTO output = finalState
                .flatMap(state -> state.value(Constant.ANSWER_RAG_NODE_OUTPUT, AnswerRAGOutputDTO.class))
                .orElse(new AnswerRAGOutputDTO(List.of()));
        // 同步提取问题重写节点的输出，供上层评估链路透传。
        AnswerRewriteOutputDTO rewriteOutput = finalState
                .flatMap(state -> state.value(Constant.ANSWER_REWRITE_NODE_OUTPUT, AnswerRewriteOutputDTO.class))
                .orElse(new AnswerRewriteOutputDTO(List.of()));
        List<AnswerRAGSegmentDTO> segments = output.getSegments() == null ? List.of() : output.getSegments();
        List<String> rewrittenQuestions = rewriteOutput.getRewrittenQueries() == null
                ? List.of()
                : rewriteOutput.getRewrittenQueries();
        return new AnswerHitKGraphResult(WorkflowConverter.toAnswerRagSegmentResults(segments), rewrittenQuestions);
    }
}
