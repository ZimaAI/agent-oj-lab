package com.oj.agent.core.workflow.util;

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class NodeBeanUtil {

    private final ApplicationContext context;

    public <T extends NodeAction> NodeAction getNodeBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    public <T extends NodeAction> AsyncNodeAction getNodeBeanAsync(Class<T> clazz) {
        return AsyncNodeAction.node_async(getNodeBean(clazz));
    }

    public <T extends EdgeAction> EdgeAction getEdgeBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    public <T extends EdgeAction> AsyncEdgeAction getEdgeBeanAsync(Class<T> clazz) {
        return AsyncEdgeAction.edge_async(getEdgeBean(clazz));
    }
}
