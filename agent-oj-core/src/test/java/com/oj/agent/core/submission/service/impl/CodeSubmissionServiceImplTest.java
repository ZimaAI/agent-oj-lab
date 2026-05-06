package com.oj.agent.core.submission.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.executor.tool.ExecuteCodeTool;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.service.AlgorithmCodeService;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.submission.model.command.CodeRunCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeSubmissionServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();

    @Mock
    private ExecuteCodeTool executeCodeTool;

    @Mock
    private AlgorithmQuestionService algorithmQuestionService;

    @Mock
    private AlgorithmCodeService algorithmCodeService;

    @InjectMocks
    private CodeSubmissionServiceImpl codeSubmissionService;

    @Test
    void executeCodeShouldUseOnlyPublicCasesFromUnifiedCasePool() throws Exception {
        when(executeCodeTool.getSupportedLanguages()).thenReturn(Set.of(Language.PYTHON));
        when(executeCodeTool.call(any())).thenReturn("{\"success\":true,\"results\":[],\"errorMessage\":null}");
        when(algorithmCodeService.getCodeTemplateByQuestionIdAndLanguage(1L, "PYTHON"))
                .thenReturn(new AlgorithmCodeTemplateResult("PYTHON", "solve", "def solve():\n    pass"));

        AlgorithmQuestionResult question = new AlgorithmQuestionResult();
        question.setId(1L);
        question.setDescription("sum two integers");
        question.setSharedTestCases("""
                [
                  {"stdin":"1 2\\n","expectedStdout":"3\\n","publicCase":true},
                  {"stdin":"9 9\\n","expectedStdout":"18\\n","publicCase":false}
                ]
                """);
        when(algorithmQuestionService.getQuestion(any())).thenReturn(question);

        CodeRunCommand command = new CodeRunCommand();
        command.setAlgorithmQuestionId(1L);
        command.setLanguage("PYTHON");
        command.setCode("def solve():\n    return 3");

        codeSubmissionService.executeCode(command);

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(executeCodeTool).call(requestCaptor.capture());
        Map<String, Object> requestPayload = OBJECT_MAPPER.readValue(requestCaptor.getValue(), Map.class);

        assertThat(requestPayload.get("functionName")).isEqualTo("solve");
        assertThat((List<?>) requestPayload.get("testInputs")).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Object> expectedOutputs = (List<Object>) requestPayload.get("expectedOutputs");
        assertThat(expectedOutputs).containsExactly("3\n");
    }
}
