package com.oj.agent.core.workflow.persistence.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class WorkflowPersistenceContext {

    public static final String GENERATED_QUESTION_ID = "generatedQuestionId";
    public static final String GENERATED_QUESTION_ASSEMBLE_OUTPUT = "generatedQuestionAssembleOutput";

    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        if (value == null) {
            values.remove(key);
            return;
        }
        values.put(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = values.get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public void setGeneratedQuestionId(Long generatedQuestionId) {
        put(GENERATED_QUESTION_ID, generatedQuestionId);
    }

    public Long getGeneratedQuestionId() {
        return get(GENERATED_QUESTION_ID, Long.class);
    }

    public void setAssembleOutput(Object assembleOutput) {
        put(GENERATED_QUESTION_ASSEMBLE_OUTPUT, assembleOutput);
    }

    public <T> T getAssembleOutput(Class<T> type) {
        return get(GENERATED_QUESTION_ASSEMBLE_OUTPUT, type);
    }
}
