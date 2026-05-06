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
import com.oj.agent.core.workflow.model.result.RagJudgeResult;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class JudgeResultDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取 RAG 判断结果
		RagJudgeResult judgeOutput = StateUtil.getObjectValue(state, Constant.RAG_JUDGE_NODE_OUTPUT,
				RagJudgeResult.class, (RagJudgeResult) null);

		if (judgeOutput == null || !judgeOutput.isMatched()) {
			log.info("RAG judge result is null or not matched, routing to CODE_QUESTION_NODE");
			return Constant.CODE_QUESTION_NODE;
		}

		log.info("RAG judge matched question: {}, routing to OJ_ASSISTANT_NODE",
				judgeOutput.getSelectedQuestion() != null ? judgeOutput.getSelectedQuestion().getTitle() : "unknown");
		return Constant.OJ_ASSISTANT_NODE;
	}

}
