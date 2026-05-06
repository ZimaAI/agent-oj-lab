package com.oj.agent.core.workflow.persistence;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;


public interface WorkflowPersistenceChain {

    /**
     * 顺序执行全部持久化节点
     *
     * @param state workflow 终态
     * @param context 责任链上下文
     */
    void persist(OverAllState state, WorkflowPersistenceContext context);
}
