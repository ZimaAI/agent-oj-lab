package com.oj.agent.core.workflow.persistence;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;


@Slf4j
@Component
public class DefaultWorkflowPersistenceChain implements WorkflowPersistenceChain {

    private final List<WorkflowPersistenceStrategy> strategies;

    public DefaultWorkflowPersistenceChain(List<WorkflowPersistenceStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(WorkflowPersistenceStrategy::getOrder))
                .toList();
    }

    @Override
    public void persist(OverAllState state, WorkflowPersistenceContext context) {
        String conversationId = (String) state.value(Constant.CONVERSATION_ID).orElse("unknown");
        String traceId = (String) state.value(Constant.TRACE_ID).orElse("unknown");

        // 按顺序执行全部持久化策略。
        for (WorkflowPersistenceStrategy strategy : strategies) {
            try {
                strategy.persist(state, context);
            } catch (Exception e) {
                log.error("Persistence strategy {} failed, conversationId={}, traceId={}",
                        strategy.getClass().getSimpleName(), conversationId, traceId, e);
            }
        }
    }
}
