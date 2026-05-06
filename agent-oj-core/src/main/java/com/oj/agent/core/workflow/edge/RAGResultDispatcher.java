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
import com.oj.agent.core.workflow.model.dto.QuestionRAGOutputDTO;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class RAGResultDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取 RAG 检索结果
		QuestionRAGOutputDTO ragOutput = StateUtil.getObjectValue(state, Constant.QUESTION_RAG_NODE_OUTPUT,
				QuestionRAGOutputDTO.class);

		if (ragOutput == null || ragOutput.getCandidates() == null || ragOutput.getCandidates().isEmpty()) {
			log.info("RAG candidates is null or empty, routing to CODE_QUESTION_NODE");
			return Constant.CODE_QUESTION_NODE;
		}

		log.info("RAG candidates found {} questions, routing to RAG_JUDGE_NODE", ragOutput.getCandidates().size());
		return Constant.RAG_JUDGE_NODE;
	}

}
