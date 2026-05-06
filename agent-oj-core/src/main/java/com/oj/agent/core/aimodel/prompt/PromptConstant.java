package com.oj.agent.core.aimodel.prompt;

import org.springframework.ai.chat.prompt.PromptTemplate;

public class PromptConstant {

	// intent-recognition.txt
	public static PromptTemplate getIntentRecognitionPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("intent-recognition"));
	}

	// python-code-execution-rules.txt
	public static PromptTemplate getPythonCodeExecutionRulesPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("python-code-execution-rules"));
	}

	// java-code-execution-rules.txt
	public static PromptTemplate getJavaCodeExecutionRulesPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("java-code-execution-rules"));
	}

	// javascript-code-execution-rules.txt
	public static PromptTemplate getJavaScriptCodeExecutionRulesPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("javascript-code-execution-rules"));
	}

	// question-rewrite.txt
	public static PromptTemplate getQuestionRewritePromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("question-rewrite"));
	}

	// answer-rewrite.txt
	public static PromptTemplate getAnswerRewritePromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("answer-rewrite"));
	}

	// oj-assistant.txt
	public static PromptTemplate getOjAssistantPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("oj-assistant"));
	}

	// rag-judge-prompt.txt
	public static PromptTemplate getRagJudgePromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("rag-judge-prompt"));
	}

	// code-evaluation.txt
	public static PromptTemplate getCodeEvaluationPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("code-evaluation"));
	}

	public static PromptTemplate getCodeQuestionGenerationTemplate(){
		return new PromptTemplate(PromptLoader.loadPrompt("code-question-generation"));
	}

	// code-question-user.txt
	public static PromptTemplate getCodeQuestionUserPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("code-question-user"));
	}

	// language-code-generation.txt
	public static PromptTemplate getLanguageCodeGenerationPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("language-code-generation"));
	}

	// language-code-user.txt
	public static PromptTemplate getLanguageCodeUserPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("language-code-user"));
	}

	// memory-compress.txt
	public static PromptTemplate getMemoryCompressPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("memory-compress"));
	}

	// segment-hitk-question.txt
	public static PromptTemplate getSegmentHitkQuestionPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("segment-hitk-question"));
	}

	// segment-ragas-qa.txt
	public static PromptTemplate getSegmentRagasQaPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("segment-ragas-qa"));
	}

	// segment-code-description.txt
	public static PromptTemplate getSegmentCodeDescriptionPromptTemplate() {
		return new PromptTemplate(PromptLoader.loadPrompt("segment-code-description"));
	}
}
