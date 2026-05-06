package com.oj.agent.core.workflow.config;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.executor.CodeExecutor;
import com.oj.agent.core.executor.GraalJavaScriptCodeExecutor;
import com.oj.agent.core.executor.GraalPythonCodeExecutor;
import com.oj.agent.core.executor.ProcessJavaCodeExecutor;
import com.oj.agent.core.executor.tool.ExecuteCodeTool;
import com.oj.agent.core.workflow.edge.IntentRecognitionDispatcher;
import com.oj.agent.core.workflow.edge.JudgeResultDispatcher;
import com.oj.agent.core.workflow.edge.WorkflowEntryDispatcher;
import com.oj.agent.core.workflow.node.AnswerRAGNode;
import com.oj.agent.core.workflow.node.AnswerRewriteNode;
import com.oj.agent.core.workflow.node.CodeEvaluationNode;
import com.oj.agent.core.workflow.node.CodeQuestionNode;
import com.oj.agent.core.workflow.node.IntentRecognitionNode;
import com.oj.agent.core.workflow.node.JavaCodeNode;
import com.oj.agent.core.workflow.node.JavaScriptCodeNode;
import com.oj.agent.core.workflow.node.MemoryCompressNode;
import com.oj.agent.core.workflow.node.MultiLanguageCodeAssembleNode;
import com.oj.agent.core.workflow.node.OJAssistantNode;
import com.oj.agent.core.workflow.node.PythonCodeNode;
import com.oj.agent.core.workflow.node.QuestionRAGNode;
import com.oj.agent.core.workflow.node.QuestionRewriteNode;
import com.oj.agent.core.workflow.node.RAGJudgeNode;
import com.oj.agent.core.workflow.util.NodeBeanUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class AgentConfiguration {

    @Bean
    public StateGraph ojGraph(NodeBeanUtil nodeBeanUtil) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(Constant.INTENT_RECOGNITION_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.QUESTION_REWRITE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.ANSWER_REWRITE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.QUESTION_RAG_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.ANSWER_RAG_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.RAG_JUDGE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.CODE_QUESTION_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.PYTHON_CODE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.JAVA_CODE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.JAVASCRIPT_CODE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.CODE_EVALUATION_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.MEMORY_COMPRESS_NODE_OUTPUT, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.CURRENT_ALGORITHM_QUESTION, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.CURRENT_REQUEST_SEQUENCE_NO, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.CURRENT_CODE_SUBMISSION, KeyStrategy.REPLACE);
            keyStrategyMap.put(Constant.MESSAGES, KeyStrategy.APPEND);
            keyStrategyMap.put(Constant.USER_ID, KeyStrategy.REPLACE);
            return keyStrategyMap;
        };

        StateGraph stateGraph = new StateGraph("ojGraph", keyStrategyFactory);

        stateGraph.addNode(Constant.INTENT_RECOGNITION_NODE, nodeBeanUtil.getNodeBeanAsync(IntentRecognitionNode.class));
        stateGraph.addNode(Constant.QUESTION_REWRITE_NODE, nodeBeanUtil.getNodeBeanAsync(QuestionRewriteNode.class));
        stateGraph.addNode(Constant.ANSWER_REWRITE_NODE, nodeBeanUtil.getNodeBeanAsync(AnswerRewriteNode.class));
        stateGraph.addNode(Constant.QUESTION_RAG_NODE, nodeBeanUtil.getNodeBeanAsync(QuestionRAGNode.class));
        stateGraph.addNode(Constant.ANSWER_RAG_NODE, nodeBeanUtil.getNodeBeanAsync(AnswerRAGNode.class));
        stateGraph.addNode(Constant.CODE_EVALUATION_NODE, nodeBeanUtil.getNodeBeanAsync(CodeEvaluationNode.class));
        stateGraph.addNode(Constant.RAG_JUDGE_NODE, nodeBeanUtil.getNodeBeanAsync(RAGJudgeNode.class));
        stateGraph.addNode(Constant.CODE_QUESTION_NODE, nodeBeanUtil.getNodeBeanAsync(CodeQuestionNode.class));
        stateGraph.addNode(Constant.PYTHON_CODE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonCodeNode.class));
        stateGraph.addNode(Constant.JAVA_CODE_NODE, nodeBeanUtil.getNodeBeanAsync(JavaCodeNode.class));
        stateGraph.addNode(Constant.JAVASCRIPT_CODE_NODE, nodeBeanUtil.getNodeBeanAsync(JavaScriptCodeNode.class));
        stateGraph.addNode(Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE,
                nodeBeanUtil.getNodeBeanAsync(MultiLanguageCodeAssembleNode.class));
        stateGraph.addNode(Constant.OJ_ASSISTANT_NODE, nodeBeanUtil.getNodeBeanAsync(OJAssistantNode.class));
        stateGraph.addNode(Constant.MEMORY_COMPRESS_NODE, nodeBeanUtil.getNodeBeanAsync(MemoryCompressNode.class));

        stateGraph.addConditionalEdges(StateGraph.START, nodeBeanUtil.getEdgeBeanAsync(WorkflowEntryDispatcher.class),
                Map.of(
                        Constant.INTENT_RECOGNITION_NODE, Constant.INTENT_RECOGNITION_NODE,
                        Constant.CODE_EVALUATION_NODE, Constant.CODE_EVALUATION_NODE
                ));
        stateGraph.addConditionalEdges(Constant.INTENT_RECOGNITION_NODE, nodeBeanUtil.getEdgeBeanAsync(IntentRecognitionDispatcher.class),
                Map.of(
                        Constant.QUESTION_REWRITE_NODE, Constant.QUESTION_REWRITE_NODE,
                        Constant.ANSWER_REWRITE_NODE, Constant.ANSWER_REWRITE_NODE,
                        Constant.CODE_EVALUATION_NODE, Constant.CODE_EVALUATION_NODE,
                        Constant.OJ_ASSISTANT_NODE, Constant.OJ_ASSISTANT_NODE
                ));
        stateGraph.addEdge(Constant.ANSWER_REWRITE_NODE, Constant.ANSWER_RAG_NODE);
        stateGraph.addEdge(Constant.ANSWER_RAG_NODE, Constant.OJ_ASSISTANT_NODE);
        stateGraph.addEdge(Constant.QUESTION_REWRITE_NODE, Constant.QUESTION_RAG_NODE);
        stateGraph.addEdge(Constant.QUESTION_RAG_NODE, Constant.RAG_JUDGE_NODE);
        stateGraph.addConditionalEdges(Constant.RAG_JUDGE_NODE,
                nodeBeanUtil.getEdgeBeanAsync(JudgeResultDispatcher.class),
                Map.of(
                        Constant.CODE_QUESTION_NODE, Constant.CODE_QUESTION_NODE,
                        Constant.OJ_ASSISTANT_NODE, Constant.OJ_ASSISTANT_NODE
                ));
        stateGraph.addEdge(Constant.CODE_QUESTION_NODE, Constant.PYTHON_CODE_NODE);
        stateGraph.addEdge(Constant.CODE_QUESTION_NODE, Constant.JAVA_CODE_NODE);
        stateGraph.addEdge(Constant.CODE_QUESTION_NODE, Constant.JAVASCRIPT_CODE_NODE);
        stateGraph.addEdge(Constant.PYTHON_CODE_NODE, Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE);
        stateGraph.addEdge(Constant.JAVA_CODE_NODE, Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE);
        stateGraph.addEdge(Constant.JAVASCRIPT_CODE_NODE, Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE);
        stateGraph.addEdge(Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE, Constant.OJ_ASSISTANT_NODE);
        stateGraph.addEdge(Constant.CODE_EVALUATION_NODE, Constant.OJ_ASSISTANT_NODE);
        stateGraph.addEdge(Constant.OJ_ASSISTANT_NODE, Constant.MEMORY_COMPRESS_NODE);
        stateGraph.addEdge(Constant.MEMORY_COMPRESS_NODE, StateGraph.END);

        return stateGraph;
    }

    @Bean
    public GraalPythonCodeExecutor graalPythonCodeExecutor() {
        return new GraalPythonCodeExecutor();
    }

    @Bean
    public GraalJavaScriptCodeExecutor graalJavaScriptCodeExecutor() {
        return new GraalJavaScriptCodeExecutor();
    }

    @Bean
    public ProcessJavaCodeExecutor processJavaCodeExecutor() {
        return new ProcessJavaCodeExecutor();
    }

    @Bean
    public ExecuteCodeTool executePythonCodeTool(List<CodeExecutor> codeExecutors) {
        return new ExecuteCodeTool(codeExecutors);
    }
}
