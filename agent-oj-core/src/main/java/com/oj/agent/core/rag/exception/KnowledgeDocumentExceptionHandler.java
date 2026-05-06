package com.oj.agent.core.rag.exception;

import com.oj.agent.common.api.Result;
import com.oj.agent.core.rag.controller.KnowledgeDocumentController;
import com.oj.agent.security.exception.SecurityErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = KnowledgeDocumentController.class)
public class KnowledgeDocumentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error("KNOWLEDGE_BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() == null
                ? "request validation failed"
                : e.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error("KNOWLEDGE_BAD_REQUEST", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Void>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(SecurityErrorCode.AUTHN_007.getCode(), e.getMessage()));
    }

    @ExceptionHandler(KnowledgeDocumentException.class)
    public ResponseEntity<Result<Void>> handleKnowledgeDocumentException(KnowledgeDocumentException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("KNOWLEDGE_PROCESS_ERROR", e.getMessage()));
    }
}
