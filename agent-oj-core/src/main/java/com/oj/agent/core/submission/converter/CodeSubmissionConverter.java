package com.oj.agent.core.submission.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.submission.model.command.CodeRunCommand;
import com.oj.agent.core.submission.model.command.CodeSubmissionCreateCommand;
import com.oj.agent.core.submission.model.command.CodeSubmissionUpdateCommand;
import com.oj.agent.core.submission.model.entity.CodeSubmission;
import com.oj.agent.core.submission.model.query.CodeSubmissionGetQuery;
import com.oj.agent.core.submission.model.query.CodeSubmissionPageQuery;
import com.oj.agent.core.submission.model.request.CodeRunRequest;
import com.oj.agent.core.submission.model.request.CodeSubmissionPageRequest;
import com.oj.agent.core.submission.model.result.CodeExecutionResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class CodeSubmissionConverter {

    private CodeSubmissionConverter() {
    }

    public static CodeRunCommand toRunCommand(CodeRunRequest request) {
        if (request == null) {
            return null;
        }
        CodeRunCommand command = new CodeRunCommand();
        command.setCode(request.getCode());
        command.setAlgorithmQuestionId(request.getAlgorithmQuestionId());
        command.setLanguage(request.getLanguage());
        return command;
    }

    public static CodeSubmissionPageQuery toPageQuery(CodeSubmissionPageRequest request) {
        CodeSubmissionPageQuery query = new CodeSubmissionPageQuery();
        if (request == null) {
            return query;
        }
        query.setCurrent(request.getCurrent());
        query.setPageSize(request.getPageSize());
        query.setAlgorithmQuestionId(request.getAlgorithmQuestionId());
        return query;
    }

    public static CodeSubmissionGetQuery toGetQuery(Long id) {
        return new CodeSubmissionGetQuery(id);
    }

    public static CodeSubmission toSubmissionEntity(CodeSubmissionCreateCommand command,
                                                    String executeStatus,
                                                    LocalDateTime now) {
        if (command == null) {
            return null;
        }
        CodeSubmission submission = new CodeSubmission();
        submission.setUserId(command.getUserId());
        submission.setAlgorithmQuestionId(command.getAlgorithmQuestionId());
        submission.setCode(command.getCode());
        submission.setLanguage(command.getLanguage());
        submission.setExecuteStatus(executeStatus);
        submission.setConversationId(command.getConversationId());
        submission.setConversationMessageId(command.getConversationMessageId());
        submission.setTraceId(command.getTraceId());
        submission.setIsDelete(0);
        submission.setCreateTime(now);
        submission.setUpdateTime(now);
        return submission;
    }

    public static CodeSubmission toUpdateEntity(CodeSubmissionUpdateCommand command, LocalDateTime now) {
        if (command == null) {
            return null;
        }
        CodeSubmission submission = new CodeSubmission();
        submission.setId(command.getId());
        submission.setExecuteStatus(command.getExecuteStatus());
        submission.setExecuteTimeMs(command.getExecuteTimeMs());
        submission.setErrorMessage(command.getErrorMessage());
        submission.setTestResults(command.getTestResults());
        submission.setPassCount(command.getPassCount());
        submission.setTotalCount(command.getTotalCount());
        submission.setUpdateTime(now);
        return submission;
    }

    public static CodeSubmissionResult toResult(CodeSubmission submission) {
        if (submission == null) {
            return null;
        }
        CodeSubmissionResult result = new CodeSubmissionResult();
        result.setId(submission.getId());
        result.setUserId(submission.getUserId());
        result.setAlgorithmQuestionId(submission.getAlgorithmQuestionId());
        result.setCode(submission.getCode());
        result.setLanguage(submission.getLanguage());
        result.setExecuteStatus(submission.getExecuteStatus());
        result.setExecuteTimeMs(submission.getExecuteTimeMs());
        result.setErrorMessage(submission.getErrorMessage());
        result.setTestResults(submission.getTestResults());
        result.setPassCount(submission.getPassCount());
        result.setTotalCount(submission.getTotalCount());
        result.setConversationId(submission.getConversationId());
        result.setConversationMessageId(submission.getConversationMessageId());
        result.setTraceId(submission.getTraceId());
        result.setCreateTime(submission.getCreateTime());
        result.setUpdateTime(submission.getUpdateTime());
        return result;
    }

    public static CodeSubmissionResult toPageResult(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        CodeSubmissionResult result = new CodeSubmissionResult();
        result.setId(toLong(row.get("id")));
        result.setAlgorithmQuestionId(toLong(row.get("algorithm_question_id")));
        result.setQuestionTitle((String) row.get("questionTitle"));
        result.setPassCount(toInteger(row.get("pass_count")));
        result.setTotalCount(toInteger(row.get("total_count")));
        result.setLanguage((String) row.get("language"));
        result.setCreateTime(toLocalDateTime(row.get("create_time")));
        return result;
    }

    public static CodeSubmissionResult toDetailResult(Map<String, Object> row) {
        CodeSubmissionResult result = toPageResult(row);
        if (result == null) {
            return null;
        }
        result.setUserId(toLong(row.get("user_id")));
        result.setCode((String) row.get("code"));
        result.setTestResults((String) row.get("test_results"));
        result.setCodeEvaluation((String) row.get("codeEvaluation"));
        return result;
    }

    public static CodeExecutionResult toExecutionResult(
            com.oj.agent.core.executor.tool.model.CodeExecutionResult executionResult) {
        if (executionResult == null) {
            return null;
        }
        CodeExecutionResult result = new CodeExecutionResult();
        result.setSuccess(executionResult.isSuccess());
        List<CodeExecutionResult.TestResultItem> testResults = executionResult.getResults() == null
                ? List.of()
                : executionResult.getResults().stream()
                .map(item -> new CodeExecutionResult.TestResultItem(
                        item.isPassed(),
                        item.getOutput(),
                        item.getError()))
                .toList();
        result.setResults(testResults);
        result.setErrorMessage(executionResult.getErrorMessage());
        return result;
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toLocalDateTime();
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        throw new IllegalStateException("Unsupported createTime type: " + value.getClass().getName());
    }
}
