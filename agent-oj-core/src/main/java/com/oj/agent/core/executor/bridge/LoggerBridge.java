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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LoggerBridge {

	private static final String PYTHON_CODE_LOGGER_NAME = "PythonCode";
	private static final String INFO_LOG_TEMPLATE = "PythonCode 信息: {}";
	private static final String DEBUG_LOG_TEMPLATE = "PythonCode 调试: {}";
	private static final String WARN_LOG_TEMPLATE = "PythonCode 警告: {}";
	private static final String ERROR_LOG_TEMPLATE = "PythonCode 错误: {}";
	private static final Logger logger = LoggerFactory.getLogger(PYTHON_CODE_LOGGER_NAME);

	public LoggerBridge() {
		// No initialization needed
	}

	/**
	 * Log at INFO level
	 */
	public void info(String message) {
		logger.info(INFO_LOG_TEMPLATE, message);
	}

	/**
	 * Log at DEBUG level
	 */
	public void debug(String message) {
		logger.debug(DEBUG_LOG_TEMPLATE, message);
	}

	/**
	 * Log at WARN level
	 */
	public void warn(String message) {
		logger.warn(WARN_LOG_TEMPLATE, message);
	}

	/**
	 * Log at ERROR level
	 */
	public void error(String message) {
		logger.error(ERROR_LOG_TEMPLATE, message);
	}
}

