package com.oj.agent.core.aimodel.exception;

import com.oj.agent.common.api.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiModelExceptionHandler {

    @ExceptionHandler(AiModelException.class)
    public Result<Void> handleAiModelException(AiModelException e) {
        return Result.error("AI_MODEL_ERROR", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation failed";
        return Result.error("VALIDATION_ERROR", message);
    }
}
