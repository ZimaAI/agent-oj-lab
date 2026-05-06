/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oj.agent.core.executor.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.executor.model.ToolCallRecord;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class AgentToolBridge {

	private static final Logger logger = LoggerFactory.getLogger(AgentToolBridge.class);

	private final Map<String, ToolCallback> tools;
	private final ObjectMapper objectMapper;
	private final List<ToolCallRecord> toolCallRecords;
	private final ToolContext toolContext;

	public AgentToolBridge(Map<String, ToolCallback> tools, ToolContext toolContext) {
		this.tools = tools;
		this.toolContext = toolContext;
		this.objectMapper = JsonUtils.getObjectMapper();
		this.toolCallRecords = new ArrayList<>();
		logger.debug("AgentToolBridge initialized with {} tools", tools.size());
	}

	/**
	 * Call a tool from Python code.
	 * This method is exported to GraalVM and can be called from Python.
	 *
	 * @param toolName the name of the tool to call
	 * @param argsJson the arguments in JSON format
	 * @return the result in JSON format
	 */
	@HostAccess.Export
	public String call(String toolName, String argsJson) {
		long startTime = System.currentTimeMillis();
		logger.info("AgentToolBridge#call - Tool: {}, Args: {}", toolName, argsJson);

		try {
			ToolCallback tool = findTool(toolName);
			Object args = parseArguments(argsJson);
			Object result = executeTool(tool, args);
			String resultJson = serializeResult(result);

			recordToolCall(toolName, startTime);

			logger.info("AgentToolBridge#call - Tool {} executed successfully", toolName);
			return resultJson;
		}
		catch (Exception e) {
			logger.error("AgentToolBridge#call - Tool {} execution failed: {}", toolName, e.getMessage(), e);
			throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Get all tool call records.
	 *
	 * @return list of tool call records
	 */
	public List<ToolCallRecord> getToolCallRecords() {
		return new ArrayList<>(toolCallRecords);
	}

	private ToolCallback findTool(String toolName) {
		ToolCallback tool = tools.get(toolName);
		if (tool == null) {
			throw new IllegalArgumentException("Tool not found: " + toolName);
		}
		return tool;
	}

	private Object parseArguments(String argsJson) {
		try {
			if (argsJson == null || argsJson.trim().isEmpty()) {
				return Map.of();
			}
			return objectMapper.readValue(argsJson, Object.class);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Invalid JSON arguments: " + e.getMessage(), e);
		}
	}

	private Object executeTool(ToolCallback tool, Object args) {
		try {
			String argsJson = objectMapper.writeValueAsString(args);
			return tool.call(argsJson, toolContext);
		}
		catch (Exception e) {
			throw new RuntimeException("Tool execution error: " + e.getMessage(), e);
		}
	}

	private String serializeResult(Object result) {
		try {
			return objectMapper.writeValueAsString(result);
		}
		catch (Exception e) {
			throw new RuntimeException("Result serialization error: " + e.getMessage(), e);
		}
	}

	private void recordToolCall(String toolName, long startTime) {
		long duration = System.currentTimeMillis() - startTime;
		int order = toolCallRecords.size() + 1;
		ToolCallRecord record = new ToolCallRecord(order, toolName);
		toolCallRecords.add(record);
		logger.debug("Tool call recorded: {} (order: {}, duration: {}ms)", toolName, order, duration);
	}

}
