/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oj.agent.core.workflow.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;


@Slf4j
@Component
public class IntentRecognitionDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取意图识别结果
		IntentRecognitionOutputDTO intentResult = StateUtil.getObjectValue(state, Constant.INTENT_RECOGNITION_NODE_OUTPUT,
				IntentRecognitionOutputDTO.class);

		if (intentResult == null || intentResult.getIntent() == null) {
			log.warn("Intent recognition result is null or empty, defaulting to END");
			return END;
		}

		UserIntentType intent = intentResult.getIntent();
		if (intent == UserIntentType.NEW_QUESTION || intent == UserIntentType.CHANGE_DIFFICULT) {
			return Constant.QUESTION_REWRITE_NODE;
		}
		if (intent == UserIntentType.CODE_EVALUATION) {
			return Constant.CODE_EVALUATION_NODE;
		}
		if (intent == UserIntentType.ANSWER) {
			return Constant.ANSWER_REWRITE_NODE;
		}
		if (intent == UserIntentType.OTHER) {
			return Constant.OJ_ASSISTANT_NODE;
		}
		return END;
	}

}
