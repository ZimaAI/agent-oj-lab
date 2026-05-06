package com.oj.agent.core.workflow.persistence;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;


public interface WorkflowPersistenceStrategy {

    /**
     * 持久化 workflow 状态数据
     * 策略内部自行判断是否需要执行持久化
     *
     * @param state workflow 状态
     * @param context 持久化责任链上下文
     */
    void persist(OverAllState state, WorkflowPersistenceContext context);

    /**
     * 兼容旧调用方式，默认使用空上下文执行。
     *
     * @param state workflow 状态
     */
    default void persist(OverAllState state) {
        persist(state, new WorkflowPersistenceContext());
    }

    /**
     * 策略执行顺序（可选，默认 0）
     * 数字越小优先级越高
     *
     * @return 执行顺序
     */
    default int getOrder() {
        return 0;
    }
}
