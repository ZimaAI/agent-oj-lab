package com.oj.agent.core.workflow.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class WorkflowEntryDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        // 读取预置意图，命中代码评审时直接跳过意图识别。
        IntentRecognitionOutputDTO intentResult = StateUtil.getOptionalObjectValueOrNull(
                state, Constant.INTENT_RECOGNITION_NODE_OUTPUT, IntentRecognitionOutputDTO.class);
        if (intentResult != null && UserIntentType.CODE_EVALUATION.equals(intentResult.getIntent())) {
            log.info("Preset intent is CODE_EVALUATION, route to code evaluation node");
            return Constant.CODE_EVALUATION_NODE;
        }
        // 默认仍然进入意图识别节点。
        return Constant.INTENT_RECOGNITION_NODE;
    }
}
