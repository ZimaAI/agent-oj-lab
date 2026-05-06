package com.oj.agent.core.submission.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.executor.tool.ExecutionComparisonPolicy;
import com.oj.agent.core.executor.tool.ExecuteCodeTool;
import com.oj.agent.core.executor.tool.model.CodeExecutionRequest;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.service.AlgorithmCodeService;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.submission.converter.CodeSubmissionConverter;
import com.oj.agent.core.submission.mapper.CodeSubmissionMapper;
import com.oj.agent.core.submission.model.command.CodeRunCommand;
import com.oj.agent.core.submission.model.command.CodeSubmissionCreateCommand;
import com.oj.agent.core.submission.model.command.CodeSubmissionUpdateCommand;
import com.oj.agent.core.submission.model.entity.CodeSubmission;
import com.oj.agent.core.submission.model.query.CodeSubmissionGetQuery;
import com.oj.agent.core.submission.model.query.CodeSubmissionPageQuery;
import com.oj.agent.core.submission.model.result.CodeExecutionResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;
import com.oj.agent.core.submission.service.CodeSubmissionService;
import com.oj.agent.security.util.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CodeSubmissionServiceImpl extends ServiceImpl<CodeSubmissionMapper, CodeSubmission>
        implements CodeSubmissionService {

    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    private static final String SUBMISSION_STATUS_PENDING = "PENDING";

    private record ResolvedRunContext(CodeRunCommand command, AlgorithmQuestionResult question) {
    }

    private final ExecuteCodeTool executeCodeTool;
    private final AlgorithmQuestionService algorithmQuestionService;
    private final AlgorithmCodeService algorithmCodeService;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public CodeSubmissionServiceImpl(ExecuteCodeTool executeCodeTool,
                                     AlgorithmQuestionService algorithmQuestionService,
                                     AlgorithmCodeService algorithmCodeService) {
        this.executeCodeTool = executeCodeTool;
        this.algorithmQuestionService = algorithmQuestionService;
        this.algorithmCodeService = algorithmCodeService;
    }

    @Override
    public CodeSubmissionResult createPendingSubmission(CodeSubmissionCreateCommand command) {
        validateCreateCommand(command);
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), java.time.ZoneId.systemDefault());
        CodeSubmission submission = CodeSubmissionConverter.toSubmissionEntity(
                command,
                SUBMISSION_STATUS_PENDING,
                now
        );
        int insertedRows = baseMapper.insert(submission);
        if (insertedRows <= 0) {
            throw new IllegalStateException("Failed to create pending submission");
        }
        return CodeSubmissionConverter.toResult(submission);
    }

    @Override
    public boolean updateSubmission(CodeSubmissionUpdateCommand command) {
        if (command == null || command.getId() == null || command.getId() <= 0) {
            return false;
        }
        CodeSubmission submission = CodeSubmissionConverter.toUpdateEntity(command, LocalDateTime.now());
        return baseMapper.updateById(submission) > 0;
    }

    @Override
    public CodeExecutionResult executeCode(CodeRunCommand command) {
        ResolvedRunContext runContext = resolveRunCommand(command);
        CodeRunCommand resolvedCommand = runContext.command();
        AlgorithmQuestionResult question = runContext.question();
        Language language = Language.fromName(resolvedCommand.getLanguage());
        if (!executeCodeTool.getSupportedLanguages().contains(language)) {
            throw new IllegalArgumentException("Unsupported execution language: " + language.getName());
        }

        CodeExecutionRequest executionRequest = new CodeExecutionRequest();
        executionRequest.setLanguage(language.getName());
        executionRequest.setCode(resolvedCommand.getCode());
        executionRequest.setFunctionName(resolvedCommand.getFunctionName());
        executionRequest.setTestInputs(resolvedCommand.getTestInputs());

        List<Object> expectedOutputs = parseExpectedOutputs(question.getSharedTestCases());
        if (!expectedOutputs.isEmpty() && expectedOutputs.size() == resolvedCommand.getTestInputs().size()) {
            executionRequest.setExpectedOutputs(expectedOutputs);
        }
        if (ExecutionComparisonPolicy.shouldIgnoreCollectionOrder(question.getDescription())) {
            executionRequest.setIgnoreCollectionOrder(true);
        }

        String toolResponse = executeCodeTool.call(writeJson(executionRequest));
        return CodeSubmissionConverter.toExecutionResult(readExecutionResult(toolResponse));
    }

    @Override
    public Page<CodeSubmissionResult> pageSubmissions(CodeSubmissionPageQuery query) {
        CodeSubmissionPageQuery safeQuery = query == null ? new CodeSubmissionPageQuery() : query;
        validatePageParams(safeQuery.getCurrent(), safeQuery.getPageSize());
        Long userId = requireCurrentUserId();

        Page<CodeSubmission> page = new Page<>(safeQuery.getCurrent(), safeQuery.getPageSize());
        IPage<Map<String, Object>> submissionPage =
                baseMapper.selectSubmissionPage(page, userId, safeQuery.getAlgorithmQuestionId());

        Page<CodeSubmissionResult> resultPage =
                new Page<>(safeQuery.getCurrent(), safeQuery.getPageSize(), submissionPage.getTotal());
        resultPage.setRecords(submissionPage.getRecords().stream()
                .map(CodeSubmissionConverter::toPageResult)
                .toList());
        return resultPage;
    }

    @Override
    public CodeSubmissionResult getSubmission(CodeSubmissionGetQuery query) {
        Long submissionId = query == null ? null : query.getId();
        if (submissionId == null || submissionId <= 0) {
            throw new IllegalArgumentException("Invalid submission id");
        }
        Long userId = requireCurrentUserId();
        Map<String, Object> detail = baseMapper.selectSubmissionDetail(submissionId, userId);
        if (detail == null || detail.isEmpty()) {
            throw new IllegalArgumentException("Submission does not exist");
        }
        return CodeSubmissionConverter.toDetailResult(detail);
    }

    private void validateCreateCommand(CodeSubmissionCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (command.getUserId() == null || command.getUserId() <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (command.getAlgorithmQuestionId() == null || command.getAlgorithmQuestionId() <= 0) {
            throw new IllegalArgumentException("algorithmQuestionId is required");
        }
        if (!StringUtils.hasText(command.getCode())) {
            throw new IllegalArgumentException("Code cannot be empty");
        }
        if (!StringUtils.hasText(command.getLanguage())) {
            throw new IllegalArgumentException("Language cannot be empty");
        }
    }

    private ResolvedRunContext resolveRunCommand(CodeRunCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (!StringUtils.hasText(command.getCode())) {
            throw new IllegalArgumentException("Code cannot be empty");
        }
        if (!StringUtils.hasText(command.getLanguage())) {
            throw new IllegalArgumentException("Language cannot be empty");
        }
        if (command.getAlgorithmQuestionId() == null || command.getAlgorithmQuestionId() <= 0) {
            throw new IllegalArgumentException("algorithmQuestionId is required");
        }

        CodeRunCommand resolvedCommand = new CodeRunCommand();
        resolvedCommand.setCode(command.getCode());
        resolvedCommand.setAlgorithmQuestionId(command.getAlgorithmQuestionId());
        resolvedCommand.setLanguage(command.getLanguage());
        resolvedCommand.setFunctionName(command.getFunctionName());
        resolvedCommand.setTestInputs(command.getTestInputs());

        AlgorithmQuestionResult question = fillRunConfigFromQuestionIfNeeded(resolvedCommand);
        validateResolvedRunCommand(resolvedCommand);
        return new ResolvedRunContext(resolvedCommand, question);
    }

    private AlgorithmQuestionResult fillRunConfigFromQuestionIfNeeded(CodeRunCommand command) {
        AlgorithmQuestionResult question = getRequiredQuestion(command.getAlgorithmQuestionId());
        AlgorithmCodeTemplateResult codeMetadata = getRequiredCodeMetadata(command.getAlgorithmQuestionId(),
                command.getLanguage());

        command.setFunctionName(codeMetadata.getFunctionName());
        if (command.getTestInputs() == null) {
            command.setTestInputs(parseTestInputs(question.getSharedTestCases()));
        }
        return question;
    }

    private void validateResolvedRunCommand(CodeRunCommand command) {
        if (!StringUtils.hasText(command.getFunctionName())) {
            throw new IllegalArgumentException("Function name cannot be empty");
        }
        if (command.getTestInputs() == null) {
            throw new IllegalArgumentException("testInputs cannot be null");
        }
        for (Map<String, Object> testInput : command.getTestInputs()) {
            if (testInput == null) {
                throw new IllegalArgumentException("testInputs contains null test case");
            }
        }
    }

    private List<Map<String, Object>> parseTestInputs(String testCasesJson) {
        if (!StringUtils.hasText(testCasesJson)) {
            throw new IllegalArgumentException("testCases cannot be empty");
        }
        try {
            JsonNode testCasesNode = objectMapper.readTree(testCasesJson);
            if (!testCasesNode.isArray()) {
                throw new IllegalArgumentException("testCases must be an array");
            }

            List<Map<String, Object>> testInputs = new ArrayList<>();
            for (JsonNode testCaseNode : testCasesNode) {
                JsonNode inputNode = testCaseNode.get("input");
                if (inputNode == null || !inputNode.isObject()) {
                    throw new IllegalArgumentException("Each test case must have an object input");
                }
                testInputs.add(objectMapper.convertValue(inputNode, STRING_OBJECT_MAP_TYPE));
            }
            return testInputs;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse testCases", e);
        }
    }

    private List<Object> parseExpectedOutputs(String testCasesJson) {
        if (!StringUtils.hasText(testCasesJson)) {
            return List.of();
        }
        try {
            JsonNode testCasesNode = objectMapper.readTree(testCasesJson);
            if (!testCasesNode.isArray()) {
                throw new IllegalArgumentException("testCases must be an array");
            }

            List<Object> expectedOutputs = new ArrayList<>();
            for (JsonNode testCaseNode : testCasesNode) {
                expectedOutputs.add(toObjectValue(testCaseNode.get("expectedOutput")));
            }
            return expectedOutputs;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse testCases", e);
        }
    }

    private Object toObjectValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private AlgorithmQuestionResult getRequiredQuestion(Long questionId) {
        AlgorithmQuestionResult question = getQuestionOrNull(questionId);
        if (question == null) {
            throw new IllegalArgumentException("Algorithm question does not exist");
        }
        if (!StringUtils.hasText(question.getSharedTestCases())) {
            throw new IllegalArgumentException("Shared testCases cannot be empty");
        }
        return question;
    }

    private AlgorithmCodeTemplateResult getRequiredCodeMetadata(Long questionId, String language) {
        AlgorithmCodeTemplateResult codeMetadata =
                algorithmCodeService.getCodeTemplateByQuestionIdAndLanguage(questionId, language);
        if (codeMetadata == null || !StringUtils.hasText(codeMetadata.getFunctionName())) {
            throw new IllegalArgumentException("Language code metadata does not exist for language: " + language);
        }
        return codeMetadata;
    }

    private AlgorithmQuestionResult getQuestion(Long questionId) {
        return algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
    }

    private AlgorithmQuestionResult getQuestionOrNull(Long questionId) {
        try {
            return getQuestion(questionId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private com.oj.agent.core.executor.tool.model.CodeExecutionResult readExecutionResult(String toolResponse) {
        try {
            return objectMapper.readValue(toolResponse, com.oj.agent.core.executor.tool.model.CodeExecutionResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse execution result", e);
        }
    }

    private String writeJson(CodeExecutionRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize execution request", e);
        }
    }

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("Current user is not authenticated");
        }
        return userId;
    }

    private void validatePageParams(long current, long pageSize) {
        if (current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("Invalid page parameters");
        }
    }
}
