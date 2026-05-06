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
package com.oj.agent.core.workflow.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oj.agent.core.workflow.enums.TextType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphNodeResponse {

    @JsonProperty("conservationId")
    private String conversationId;

    private String nodeName;

    private TextType textType;

    private String text;

    @Builder.Default
    private boolean error = false;

    @Builder.Default
    private boolean complete = false;

    public static GraphNodeResponse error(String threadId, String nodeName, String text) {
        return GraphNodeResponse.builder()
                .conversationId(threadId)
                .nodeName(nodeName)
                .text(text)
                .error(true)
                .textType(TextType.TEXT)
                .build();
    }

    public static GraphNodeResponse complete(String threadId, String nodeName) {
        return GraphNodeResponse.builder()
                .conversationId(threadId)
                .nodeName(nodeName)
                .complete(true)
                .textType(TextType.TEXT)
                .build();
    }
}
