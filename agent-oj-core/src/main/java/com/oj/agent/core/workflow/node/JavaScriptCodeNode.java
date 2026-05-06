package com.oj.agent.core.workflow.node;

import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.executor.tool.ExecuteCodeTool;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import org.springframework.stereotype.Component;

@Component
public class JavaScriptCodeNode extends AbstractLanguageCodeNode {

    public JavaScriptCodeNode(AiModelRegistry aiModelRegistry,
                              ExecuteCodeTool executeCodeTool,
                              WorkflowTraceRecorder workflowTraceRecorder,
                              WorkflowTraceSupport workflowTraceSupport) {
        super(aiModelRegistry,
                executeCodeTool,
                Language.JAVASCRIPT,
                Constant.JAVASCRIPT_CODE_NODE_OUTPUT,
                workflowTraceRecorder,
                workflowTraceSupport);
    }

    @Override
    protected String resolveExecutionRulesPrompt() {
        return PromptHelper.buildJavaScriptCodeExecutionRulesPrompt();
    }
}
