package com.oj.agent.security.exception;

/**
 * Validation exception for bad client input.
 */
public class ValidationException extends RuntimeException {

    private final String errorCode;

    public ValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
