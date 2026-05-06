package com.oj.agent.core.executor.model;

import com.oj.agent.core.question.enums.Language;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GeneratedCode implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String STRING_TEMPLATE = "GeneratedCode{functionName='%s', language=%s, description='%s', createdAt=%s}";

    private String functionName;

    private Language language;

    private String code;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String originalQuery;

    private List<String> parameters;

    private String functionSignature;

    public GeneratedCode() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.parameters = new ArrayList<>();
    }

    public GeneratedCode(String functionName, Language language, String code, String description) {
        this();
        this.functionName = functionName;
        this.language = language;
        this.code = code;
        this.description = description;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public String getFunctionSignature() {
        return functionSignature;
    }

    public void setFunctionSignature(String functionSignature) {
        this.functionSignature = functionSignature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GeneratedCode that = (GeneratedCode) o;
        return Objects.equals(functionName, that.functionName) && language == that.language;
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionName, language);
    }

    @Override
    public String toString() {
        return STRING_TEMPLATE.formatted(functionName, language, description, createdAt);
    }
}
